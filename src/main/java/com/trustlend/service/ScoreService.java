package com.trustlend.service;

import com.trustlend.config.AppConfig;
import com.trustlend.dao.LoanDao;
import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.ScoreHistoryDao;
import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.engine.EngineSettings;
import com.trustlend.engine.TrustScoreEngine;
import com.trustlend.engine.VendorProfile;
import com.trustlend.exception.DataAccessException;
import com.trustlend.graph.TrustGraph;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.RiskBand;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.Vouch;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Loads data through the DAOs, runs the {@link TrustScoreEngine}, caches the latest scores
 * and persists a daily snapshot to score_history.
 */
public class ScoreService {

    private static final Logger LOG = Logger.getLogger(ScoreService.class.getName());

    private final VendorDao vendorDao;
    private final TransactionDao transactionDao;
    private final LoanDao loanDao;
    private final RepaymentDao repaymentDao;
    private final VouchDao vouchDao;
    private final ScoreHistoryDao historyDao;

    private TrustScoreEngine engine;
    private volatile Map<Long, ScoreBreakdown> latest = Map.of();
    private volatile TrustGraph<Long> graph = new TrustGraph<>();

    public ScoreService(VendorDao vendorDao, TransactionDao transactionDao, LoanDao loanDao,
                        RepaymentDao repaymentDao, VouchDao vouchDao, ScoreHistoryDao historyDao,
                        EngineSettings settings) {
        this.vendorDao = vendorDao;
        this.transactionDao = transactionDao;
        this.loanDao = loanDao;
        this.repaymentDao = repaymentDao;
        this.vouchDao = vouchDao;
        this.historyDao = historyDao;
        this.engine = new TrustScoreEngine(settings);
    }

    /** Re-reads weights etc. from {@link AppConfig} (after the Settings screen saves). */
    public synchronized void reloadSettings() {
        engine = new TrustScoreEngine(EngineSettings.fromConfig(AppConfig.getInstance()));
    }

    public EngineSettings getSettings() {
        return engine.getSettings();
    }

    /** Loads every vendor's data in a handful of queries and groups it in memory. */
    public List<VendorProfile> loadProfiles() throws DataAccessException {
        List<Vendor> vendors = vendorDao.findAll();
        Map<Long, List<Transaction>> txns = new HashMap<>();
        for (Transaction t : transactionDao.findAll()) {
            txns.computeIfAbsent(t.getVendorId(), k -> new ArrayList<>()).add(t);
        }
        Map<Long, List<Loan>> loans = new HashMap<>();
        Map<Long, Long> loanOwner = new HashMap<>();
        for (Loan l : loanDao.findAll()) {
            loans.computeIfAbsent(l.getVendorId(), k -> new ArrayList<>()).add(l);
            loanOwner.put(l.getId(), l.getVendorId());
        }
        Map<Long, List<Repayment>> reps = new HashMap<>();
        for (Repayment r : repaymentDao.findAll()) {
            Long owner = loanOwner.get(r.getLoanId());
            if (owner != null) {
                reps.computeIfAbsent(owner, k -> new ArrayList<>()).add(r);
            }
        }
        List<VendorProfile> profiles = new ArrayList<>();
        for (Vendor v : vendors) {
            profiles.add(new VendorProfile(v,
                    txns.getOrDefault(v.getId(), List.of()),
                    loans.getOrDefault(v.getId(), List.of()),
                    reps.getOrDefault(v.getId(), List.of())));
        }
        return profiles;
    }

    /** Scores everybody, caches the result and stores today's snapshot. */
    public synchronized Map<Long, ScoreBreakdown> recalculateAll(LocalDate today) throws DataAccessException {
        List<VendorProfile> profiles = loadProfiles();
        List<Vouch> vouches = vouchDao.findAll();
        Map<Long, ScoreBreakdown> scores = engine.computeAll(profiles, vouches, today);
        graph = engine.buildGraph(profiles, vouches);
        latest = Collections.unmodifiableMap(scores);
        historyDao.saveAll(scores.values());
        LOG.info("Recalculated " + scores.size() + " trust scores");
        return latest;
    }

    /**
     * Back-fills score history at weekly intervals so new installs have charts to show.
     * Each snapshot only sees data that existed on that date.
     */
    public void backfillHistory(LocalDate today, int weeks, int stepWeeks) throws DataAccessException {
        List<VendorProfile> profiles = loadProfiles();
        List<Vouch> vouches = vouchDao.findAll();
        for (int w = weeks; w > 0; w -= stepWeeks) {
            LocalDate date = today.minusWeeks(w);
            List<VendorProfile> past = new ArrayList<>();
            for (VendorProfile p : profiles) {
                if (!p.vendor().getJoinDate().isAfter(date)) {
                    past.add(p.asOf(date));
                }
            }
            List<Vouch> pastVouches = vouches.stream().filter(v -> !v.getCreatedDate().isAfter(date)).toList();
            historyDao.saveAll(engine.computeAll(past, pastVouches, date).values());
        }
    }

    /**
     * "What if" simulation: the vouchee's score if {@code voucherId} vouched with the given strength.
     * Nothing is saved.
     */
    public ScoreBreakdown simulateVouch(long voucherId, long voucheeId, int strength) throws DataAccessException {
        List<Vouch> vouches = new ArrayList<>(vouchDao.findAll());
        vouches.removeIf(v -> v.getVoucherId() == voucherId && v.getVoucheeId() == voucheeId);
        vouches.add(new Vouch(0, voucherId, voucheeId, strength, Vouch.Relationship.NEIGHBOURING_STALL, LocalDate.now()));
        return engine.computeAll(loadProfiles(), vouches, LocalDate.now()).get(voucheeId);
    }

    // ------------------------------------------------------------------ "what if" projection

    /**
     * A hypothetical future for one vendor, set with the sliders on the vendor profile.
     *
     * @param dailySalesWeeks   keeps selling six days a week for this many more weeks (0-12)
     * @param onTimeInstalments pays this many more weekly instalments on time (0-10)
     * @param newVouches        gets vouches from this many of the most trusted vendors (0-5)
     * @param defaults          stops paying and defaults on the current (or a new) loan
     */
    public record Scenario(int dailySalesWeeks, int onTimeInstalments, int newVouches, boolean defaults) {
        public static final Scenario NONE = new Scenario(0, 0, 0, false);

        public boolean isEmpty() {
            return equals(NONE);
        }
    }

    /** Current and projected score; {@code assumptions} lists what was simulated, in plain words. */
    public record Projection(ScoreBreakdown current, ScoreBreakdown projected, List<String> assumptions) {
        public int delta() {
            return projected.score() - current.score();
        }
    }

    /**
     * Runs the full engine (including trust propagation) on a modified copy of the data.
     * Nothing is written to the database. Other vendors are left exactly as they are today.
     */
    public synchronized Projection project(long vendorId, Scenario scenario) throws DataAccessException {
        LocalDate today = LocalDate.now();
        List<VendorProfile> profiles = loadProfiles();
        List<Vouch> vouches = new ArrayList<>(vouchDao.findAll());
        Map<Long, ScoreBreakdown> before = engine.computeAll(profiles, vouches, today);
        VendorProfile target = profiles.stream().filter(p -> p.vendorId() == vendorId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown vendor " + vendorId));
        if (scenario.isEmpty()) {
            return new Projection(before.get(vendorId), before.get(vendorId), List.of());
        }

        List<String> notes = new ArrayList<>();
        Vendor vendor = target.vendor();
        List<Transaction> txns = new ArrayList<>(target.transactions());
        List<Loan> loans = new ArrayList<>(target.loans());
        List<Repayment> reps = new ArrayList<>(target.repayments());

        // 1) N more weeks of daily trading: move the vendor's own history N weeks into the past
        //    and fill the newest N weeks with Monday-Saturday sales of their typical size.
        int weeks = scenario.dailySalesWeeks();
        if (weeks > 0) {
            vendor = new Vendor(vendor.getId(), vendor.getName(), vendor.getPhone(), vendor.getCategory(),
                    vendor.getMarketArea(), vendor.getCity(), vendor.getYearsInBusiness(),
                    vendor.getJoinDate().minusWeeks(weeks), vendor.isActive());
            double typical = typicalSale(txns);
            List<Transaction> shifted = new ArrayList<>();
            for (Transaction t : txns) {
                shifted.add(new Transaction(t.getId(), vendorId, t.getAmount(), t.getType(), t.getDate().minusWeeks(weeks), t.getNote()));
            }
            for (int d = 0; d < weeks * 7; d++) {
                LocalDate day = today.minusDays(d);
                if (day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    shifted.add(new Transaction(vendorId, typical, Transaction.Type.SALE, day, "what-if"));
                }
            }
            txns = shifted;
            notes.add("Sells 6 days a week for " + weeks + " more week(s) (about " + com.trustlend.util.Formats.inr(typical) + " a day)");
        }

        // 2) More instalments paid on time (on the open loan, or on a new ₹10,000 loan)
        Loan open = loans.stream().filter(l -> l.getStatus().isOpen()).findFirst().orElse(null);
        int k = scenario.onTimeInstalments();
        if (k > 0 && !scenario.defaults()) {
            if (open == null) {
                open = new Loan(-1, vendorId, 10_000, 18, Math.max(k, 12), today.minusWeeks(k), today.plusWeeks(Math.max(k, 12) - k),
                        LoanStatus.ACTIVE, before.get(vendorId).score());
                loans.add(open);
                notes.add("Takes a new ₹10,000 loan");
            }
            for (int i = 0; i < k; i++) {
                LocalDate due = today.minusWeeks(i);
                reps.add(new Repayment(-1, open.getId(), open.getWeeklyInstalment(), due, due, true));
            }
            notes.add("Pays " + k + " more weekly instalment(s) on time");
        }

        // 3) Vouches from the most trusted vendors who don't vouch for them yet
        if (scenario.newVouches() > 0) {
            Set<Long> already = new HashSet<>();
            vouches.stream().filter(v -> v.getVoucheeId() == vendorId).forEach(v -> already.add(v.getVoucherId()));
            Map<Long, String> names = new HashMap<>();
            profiles.forEach(p -> names.put(p.vendorId(), p.vendor().getName()));
            List<Long> vouchers = profiles.stream()
                    .filter(p -> p.vendorId() != vendorId && p.vendor().isActive() && !already.contains(p.vendorId()))
                    .map(VendorProfile::vendorId)
                    .sorted(Comparator.comparingInt((Long id) -> before.get(id).score()).reversed())
                    .limit(scenario.newVouches())
                    .toList();
            for (Long id : vouchers) {
                vouches.add(new Vouch(-1, id, vendorId, 4, Vouch.Relationship.NEIGHBOURING_STALL, today));
            }
            if (!vouchers.isEmpty()) {
                notes.add("Gets vouches from " + String.join(", ", vouchers.stream().map(names::get).toList()));
            }
        }

        // 4) Default
        if (scenario.defaults()) {
            List<Loan> changed = new ArrayList<>();
            boolean any = false;
            for (Loan l : loans) {
                if (l.getStatus().isOpen()) {
                    changed.add(new Loan(l.getId(), vendorId, l.getPrincipal(), l.getInterestRate(), l.getTenureWeeks(),
                            l.getIssueDate(), l.getDueDate(), LoanStatus.DEFAULTED, l.getScoreAtIssue()));
                    any = true;
                } else {
                    changed.add(l);
                }
            }
            if (!any) {
                changed.add(new Loan(-1, vendorId, 10_000, 18, 12, today.minusWeeks(16), today.minusWeeks(4),
                        LoanStatus.DEFAULTED, before.get(vendorId).score()));
            }
            loans = changed;
            notes.add("Stops paying and defaults on a loan");
        }

        List<VendorProfile> modified = new ArrayList<>();
        for (VendorProfile p : profiles) {
            modified.add(p.vendorId() == vendorId ? new VendorProfile(vendor, txns, loans, reps) : p);
        }
        ScoreBreakdown after = engine.computeAll(modified, vouches, today).get(vendorId);
        return new Projection(before.get(vendorId), after, notes);
    }

    /** Median sale amount (₹1,500 if the vendor has no sales yet). */
    private static double typicalSale(List<Transaction> txns) {
        double[] sales = txns.stream().filter(t -> t.getType() == Transaction.Type.SALE)
                .mapToDouble(Transaction::getAmount).sorted().toArray();
        return sales.length == 0 ? 1500 : Math.round(sales[sales.length / 2]);
    }

    public Optional<ScoreBreakdown> scoreOf(long vendorId) {
        return Optional.ofNullable(latest.get(vendorId));
    }

    public int scoreValue(long vendorId) {
        return scoreOf(vendorId).map(ScoreBreakdown::score).orElse(engine.getSettings().minScore());
    }

    public Map<Long, ScoreBreakdown> latest() {
        return latest;
    }

    public TrustGraph<Long> graph() {
        return graph;
    }

    public List<ScoreBreakdown> history(long vendorId) throws DataAccessException {
        return historyDao.findByVendor(vendorId);
    }

    /** Copies cached scores onto vendor objects for display. */
    public <T extends Collection<Vendor>> T decorate(T vendors) {
        for (Vendor v : vendors) {
            int s = scoreValue(v.getId());
            v.setCurrentScore(s);
            v.setRiskBand(RiskBand.fromScore(s));
        }
        return vendors;
    }
}
