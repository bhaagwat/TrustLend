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
import com.trustlend.model.Repayment;
import com.trustlend.model.RiskBand;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.Vouch;

import java.time.LocalDate;
import java.util.ArrayList;
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
