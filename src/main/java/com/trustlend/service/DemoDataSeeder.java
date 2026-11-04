package com.trustlend.service;

import com.trustlend.dao.LoanDao;
import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.model.Vouch;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generates a realistic, reproducible demo community (fixed random seed) so the app is
 * interesting on first launch: steady earners, newcomers boosted by vouches, irregular
 * traders and a few defaulters whose vouchers lose credibility.
 */
public class DemoDataSeeder {

    private static final Logger LOG = Logger.getLogger(DemoDataSeeder.class.getName());

    /** Behaviour profiles used only to generate data — the engine never sees these labels. */
    private enum Archetype {
        STAR(0.92, 0.12, 0.8, 0.97),
        STEADY(0.82, 0.22, 0.5, 0.88),
        NEWCOMER(0.78, 0.22, 0.4, 0.9),
        IRREGULAR(0.55, 0.65, 0.1, 0.6),
        STRUGGLING(0.45, 0.45, 0.1, 0.55),
        DEFAULTER(0.5, 0.5, 0.05, 0.5);

        final double saleProbability;
        final double volatility;
        final double depositProbability;
        final double onTimeProbability;

        Archetype(double sale, double vol, double dep, double onTime) {
            this.saleProbability = sale;
            this.volatility = vol;
            this.depositProbability = dep;
            this.onTimeProbability = onTime;
        }
    }

    private record Seed(String name, VendorCategory category, String market, Archetype type) { }

    private static final List<Seed> SEEDS = List.of(
            new Seed("Ramesh Kumar", VendorCategory.FRUITS_VEGETABLES, "Chandni Chowk", Archetype.STAR),
            new Seed("Sunita Devi", VendorCategory.STREET_FOOD, "Chandni Chowk", Archetype.STAR),
            new Seed("Mohammed Irfan", VendorCategory.MOBILE_REPAIR, "Chandni Chowk", Archetype.STEADY),
            new Seed("Kavita Sharma", VendorCategory.FLOWERS, "Chandni Chowk", Archetype.STEADY),
            new Seed("Deepak Yadav", VendorCategory.TEA_STALL, "Chandni Chowk", Archetype.NEWCOMER),
            new Seed("Anil Gupta", VendorCategory.KIRANA, "Chandni Chowk", Archetype.IRREGULAR),
            new Seed("Pooja Verma", VendorCategory.TAILORING, "Chandni Chowk", Archetype.STEADY),
            new Seed("Salim Khan", VendorCategory.CLOTHING, "Chandni Chowk", Archetype.DEFAULTER),

            new Seed("Lakshmi Iyer", VendorCategory.FLOWERS, "Sarojini Nagar", Archetype.STAR),
            new Seed("Rajesh Paswan", VendorCategory.CLOTHING, "Sarojini Nagar", Archetype.STEADY),
            new Seed("Neha Singh", VendorCategory.HANDICRAFTS, "Sarojini Nagar", Archetype.NEWCOMER),
            new Seed("Vikram Chauhan", VendorCategory.STREET_FOOD, "Sarojini Nagar", Archetype.STEADY),
            new Seed("Farida Begum", VendorCategory.TAILORING, "Sarojini Nagar", Archetype.STAR),
            new Seed("Suresh Mehra", VendorCategory.TEA_STALL, "Sarojini Nagar", Archetype.STRUGGLING),
            new Seed("Arjun Thakur", VendorCategory.MOBILE_REPAIR, "Sarojini Nagar", Archetype.IRREGULAR),

            new Seed("Geeta Rani", VendorCategory.FRUITS_VEGETABLES, "Lajpat Nagar", Archetype.STAR),
            new Seed("Harish Bansal", VendorCategory.KIRANA, "Lajpat Nagar", Archetype.STEADY),
            new Seed("Meena Kumari", VendorCategory.STREET_FOOD, "Lajpat Nagar", Archetype.NEWCOMER),
            new Seed("Imran Qureshi", VendorCategory.CLOTHING, "Lajpat Nagar", Archetype.STEADY),
            new Seed("Rekha Joshi", VendorCategory.HANDICRAFTS, "Lajpat Nagar", Archetype.IRREGULAR),
            new Seed("Manoj Tiwari", VendorCategory.TEA_STALL, "Lajpat Nagar", Archetype.DEFAULTER),
            new Seed("Asha Pillai", VendorCategory.FLOWERS, "Lajpat Nagar", Archetype.STRUGGLING),

            new Seed("Gurpreet Kaur", VendorCategory.STREET_FOOD, "Karol Bagh", Archetype.STAR),
            new Seed("Rakesh Saini", VendorCategory.FRUITS_VEGETABLES, "Karol Bagh", Archetype.STEADY),
            new Seed("Shabnam Ali", VendorCategory.TAILORING, "Karol Bagh", Archetype.NEWCOMER),
            new Seed("Dinesh Rawat", VendorCategory.MOBILE_REPAIR, "Karol Bagh", Archetype.STEADY),
            new Seed("Priya Nair", VendorCategory.HANDICRAFTS, "Karol Bagh", Archetype.IRREGULAR),
            new Seed("Bablu Prasad", VendorCategory.TEA_STALL, "Karol Bagh", Archetype.STRUGGLING),

            new Seed("Santosh Mishra", VendorCategory.KIRANA, "Paharganj", Archetype.STAR),
            new Seed("Rani Kashyap", VendorCategory.FRUITS_VEGETABLES, "Paharganj", Archetype.STEADY),
            new Seed("Tenzin Dorje", VendorCategory.HANDICRAFTS, "Paharganj", Archetype.NEWCOMER),
            new Seed("Javed Ansari", VendorCategory.STREET_FOOD, "Paharganj", Archetype.IRREGULAR),
            new Seed("Kamla Bai", VendorCategory.FLOWERS, "Paharganj", Archetype.STEADY),
            new Seed("Sonu Jatav", VendorCategory.CLOTHING, "Paharganj", Archetype.DEFAULTER),
            new Seed("Nirmala Das", VendorCategory.TAILORING, "Paharganj", Archetype.NEWCOMER),
            new Seed("Pappu Lal", VendorCategory.TEA_STALL, "Paharganj", Archetype.IRREGULAR));

    private final DatabaseManager db;
    private final VendorDao vendorDao;
    private final TransactionDao transactionDao;
    private final LoanDao loanDao;
    private final RepaymentDao repaymentDao;
    private final VouchDao vouchDao;
    private final Random random = new Random(2026);

    public DemoDataSeeder(DatabaseManager db, VendorDao vendorDao, TransactionDao transactionDao,
                          LoanDao loanDao, RepaymentDao repaymentDao, VouchDao vouchDao) {
        this.db = db;
        this.vendorDao = vendorDao;
        this.transactionDao = transactionDao;
        this.loanDao = loanDao;
        this.repaymentDao = repaymentDao;
        this.vouchDao = vouchDao;
    }

    public void seed(LocalDate today) throws DataAccessException {
        long start = System.currentTimeMillis();
        db.transaction(() -> {
            Map<Vendor, Archetype> vendors = createVendors(today);
            List<Transaction> txns = new ArrayList<>();
            for (Map.Entry<Vendor, Archetype> e : vendors.entrySet()) {
                txns.addAll(generateTransactions(e.getKey(), e.getValue(), today));
            }
            transactionDao.insertBatch(txns);
            for (Map.Entry<Vendor, Archetype> e : vendors.entrySet()) {
                generateLoans(e.getKey(), e.getValue(), today);
            }
            generateVouches(vendors, today);
            return null;
        });
        LOG.info("Demo data seeded in " + (System.currentTimeMillis() - start) + " ms");
    }

    private Map<Vendor, Archetype> createVendors(LocalDate today) throws DataAccessException {
        Map<Vendor, Archetype> map = new HashMap<>();
        Set<String> phones = new HashSet<>();
        for (Seed s : SEEDS) {
            String phone;
            do {
                phone = (6 + random.nextInt(4)) + String.format("%09d", random.nextInt(1_000_000_000));
            } while (!phones.add(phone));
            LocalDate joined = switch (s.type()) {
                case STAR, STEADY -> today.minusDays(240 + random.nextInt(500));
                case NEWCOMER -> today.minusDays(20 + random.nextInt(40));
                default -> today.minusDays(150 + random.nextInt(300));
            };
            int years = switch (s.type()) {
                case STAR -> 8 + random.nextInt(13);
                case STEADY -> 4 + random.nextInt(9);
                case NEWCOMER -> 1 + random.nextInt(4);
                default -> 1 + random.nextInt(7);
            };
            Vendor v = new Vendor(s.name(), phone, s.category(), s.market(), "New Delhi", years, joined);
            vendorDao.save(v);
            map.put(v, s.type());
        }
        return map;
    }

    private double baseSale(VendorCategory c) {
        return switch (c) {
            case TEA_STALL -> 900;
            case FLOWERS -> 1100;
            case STREET_FOOD -> 1800;
            case FRUITS_VEGETABLES -> 2200;
            case TAILORING -> 1200;
            case MOBILE_REPAIR -> 1600;
            case KIRANA -> 3000;
            case CLOTHING -> 2600;
            case HANDICRAFTS -> 1400;
            case OTHER -> 1000;
        };
    }

    private List<Transaction> generateTransactions(Vendor v, Archetype a, LocalDate today) {
        List<Transaction> list = new ArrayList<>();
        LocalDate from = v.getJoinDate().isAfter(today.minusDays(150)) ? v.getJoinDate() : today.minusDays(150);
        double base = baseSale(v.getCategory()) * (0.8 + random.nextDouble() * 0.4);
        boolean onStreak = true;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.MONDAY && a == Archetype.IRREGULAR) {
                onStreak = random.nextDouble() < 0.55;
            }
            double p = a.saleProbability;
            if (a == Archetype.IRREGULAR) p = onStreak ? 0.75 : 0.05;
            if (a == Archetype.DEFAULTER && d.isAfter(today.minusWeeks(7))) p = 0.15;
            if (random.nextDouble() < p) {
                double amount = Math.max(80, base * (1 + random.nextGaussian() * a.volatility));
                list.add(new Transaction(v.getId(), Math.round(amount), Transaction.Type.SALE, d, "Daily sales"));
            }
            if (d.getDayOfWeek() == DayOfWeek.MONDAY && random.nextDouble() < 0.7 * p / a.saleProbability) {
                list.add(new Transaction(v.getId(), Math.round(base * (2.5 + random.nextDouble())),
                        Transaction.Type.PURCHASE, d, "Weekly stock"));
            }
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY && random.nextDouble() < a.depositProbability) {
                list.add(new Transaction(v.getId(), Math.round(base * (0.15 + random.nextDouble() * 0.2)),
                        Transaction.Type.DEPOSIT, d, "Savings"));
            }
        }
        return list;
    }

    private void generateLoans(Vendor v, Archetype a, LocalDate today) throws DataAccessException {
        switch (a) {
            case STAR -> {
                loan(v, today.minusWeeks(44), 15000, 12, a.onTimeProbability, Integer.MAX_VALUE, false, today);
                loan(v, today.minusWeeks(26), 25000, 16, a.onTimeProbability, Integer.MAX_VALUE, false, today);
                if (random.nextBoolean()) loan(v, today.minusWeeks(5), 30000, 20, a.onTimeProbability, Integer.MAX_VALUE, false, today);
            }
            case STEADY -> {
                loan(v, today.minusWeeks(30), 10000, 12, a.onTimeProbability, Integer.MAX_VALUE, false, today);
                if (random.nextBoolean()) loan(v, today.minusWeeks(6), 15000, 16, a.onTimeProbability, Integer.MAX_VALUE, false, today);
            }
            case IRREGULAR -> loan(v, today.minusWeeks(20), 6000, 12, a.onTimeProbability, Integer.MAX_VALUE, false, today);
            case STRUGGLING -> loan(v, today.minusWeeks(15), 5000, 12, a.onTimeProbability, 6, false, today);
            case DEFAULTER -> loan(v, today.minusWeeks(22), 8000, 12, a.onTimeProbability, 3, true, today);
            default -> { /* newcomers have no loans yet */ }
        }
    }

    private void loan(Vendor v, LocalDate issue, double principal, int tenure, double onTimeProb,
                      int instalmentsToPay, boolean defaults, LocalDate today) throws DataAccessException {
        if (issue.isBefore(v.getJoinDate())) {
            issue = v.getJoinDate().plusWeeks(2);
            if (issue.plusWeeks(2).isAfter(today)) return;
        }
        int score = 520 + random.nextInt(260);
        double rate = Math.round(Math.max(12, Math.min(30, 18 + (650 - score) * 0.02)) * 10) / 10.0;
        Loan loan = new Loan(0, v.getId(), principal, rate, tenure, issue, issue.plusWeeks(tenure),
                LoanStatus.ACTIVE, score);
        loanDao.save(loan);
        transactionDao.save(new Transaction(v.getId(), principal, Transaction.Type.LOAN_DISBURSAL, issue,
                "Loan #" + loan.getId() + " disbursed"));

        double instalment = loan.getWeeklyInstalment();
        double remaining = loan.getTotalPayable();
        int paid = 0;
        for (int n = 1; n <= tenure && paid < instalmentsToPay; n++) {
            LocalDate due = loan.instalmentDueDate(n);
            boolean onTime = random.nextDouble() < onTimeProb;
            LocalDate paidOn = onTime ? due.minusDays(random.nextInt(2)) : due.plusDays(3 + random.nextInt(8));
            if (paidOn.isAfter(today)) break;
            double amount = Math.min(instalment, remaining);
            repaymentDao.save(Repayment.of(loan.getId(), amount, due, paidOn));
            remaining -= amount;
            paid++;
            if (remaining <= 0.5) break;
        }
        LoanStatus status;
        if (remaining <= 0.5) status = LoanStatus.REPAID;
        else if (defaults) status = LoanStatus.DEFAULTED;
        else if (today.isAfter(loan.getDueDate())) status = LoanStatus.OVERDUE;
        else status = LoanStatus.ACTIVE;
        loanDao.updateStatus(loan.getId(), status);
    }

    private void generateVouches(Map<Vendor, Archetype> vendors, LocalDate today) throws DataAccessException {
        Map<String, List<Vendor>> byMarket = new HashMap<>();
        vendors.keySet().forEach(v -> byMarket.computeIfAbsent(v.getMarketArea(), k -> new ArrayList<>()).add(v));
        Set<String> made = new HashSet<>();
        Vouch.Relationship[] rels = Vouch.Relationship.values();

        for (List<Vendor> market : byMarket.values()) {
            market.sort((x, y) -> Long.compare(x.getId(), y.getId()));   // deterministic order
            for (Vendor voucher : market) {
                Archetype a = vendors.get(voucher);
                int count = switch (a) {
                    case STAR -> 3 + random.nextInt(2);
                    case STEADY -> 1 + random.nextInt(3);
                    case IRREGULAR, STRUGGLING -> random.nextInt(2);
                    default -> random.nextInt(2);
                };
                List<Vendor> candidates = new ArrayList<>(market);
                candidates.remove(voucher);
                // stars prefer to vouch for newcomers they mentor
                candidates.sort((x, y) -> Boolean.compare(vendors.get(y) == Archetype.NEWCOMER,
                        vendors.get(x) == Archetype.NEWCOMER));
                for (int i = 0; i < count && i < candidates.size(); i++) {
                    Vendor vouchee = (a == Archetype.STAR && i == 0) ? candidates.get(0)
                            : candidates.get(random.nextInt(candidates.size()));
                    int strength = switch (a) {
                        case STAR -> 4 + random.nextInt(2);
                        case STEADY -> 3 + random.nextInt(2);
                        default -> 1 + random.nextInt(3);
                    };
                    vouch(voucher, vouchee, strength, rels[random.nextInt(rels.length)], today, made);
                }
            }
        }
        // A few cross-market vouches (suppliers who serve several bazaars)
        List<Vendor> all = new ArrayList<>(vendors.keySet());
        all.sort((x, y) -> Long.compare(x.getId(), y.getId()));
        for (int i = 0; i < 6; i++) {
            Vendor a = all.get(random.nextInt(all.size()));
            Vendor b = all.get(random.nextInt(all.size()));
            if (vendors.get(a) == Archetype.STAR || vendors.get(a) == Archetype.STEADY) {
                vouch(a, b, 2 + random.nextInt(3), Vouch.Relationship.SUPPLIER, today, made);
            }
        }
    }

    private void vouch(Vendor from, Vendor to, int strength, Vouch.Relationship rel, LocalDate today,
                       Set<String> made) throws DataAccessException {
        if (from.equals(to) || !made.add(from.getId() + ">" + to.getId())) return;
        LocalDate earliest = from.getJoinDate().isAfter(to.getJoinDate()) ? from.getJoinDate() : to.getJoinDate();
        long span = Math.max(1, today.toEpochDay() - earliest.toEpochDay());
        LocalDate created = earliest.plusDays(Math.min(span, 3 + random.nextInt((int) Math.min(span, 120) + 1)));
        if (created.isAfter(today)) created = today;
        vouchDao.save(new Vouch(0, from.getId(), to.getId(), strength, rel, created));
    }
}
