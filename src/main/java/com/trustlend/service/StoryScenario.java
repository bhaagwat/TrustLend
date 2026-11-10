package com.trustlend.service;

import com.trustlend.engine.EngineSettings;
import com.trustlend.engine.TrustScoreEngine;
import com.trustlend.engine.VendorProfile;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.model.Vouch;
import com.trustlend.util.Formats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

/**
 * "Meet Sunil" — the story-mode demo. A chai seller with no credit history joins TrustLend and,
 * week by week, earns a loan. Every score shown is computed by the real {@link TrustScoreEngine}
 * on a small, self-contained community, so the story works in any workspace (even an empty one)
 * and never touches the database.
 */
public final class StoryScenario {

    public static final String HERO = "Sunil Yadav";
    public static final double MONEYLENDER_RATE = 60.0;   // ~5% a month, typical for informal lenders

    /** One animation step: the score after something happened. */
    public record Frame(String when, int score, double consistency, double repayment, double tenure,
                        double network, String event, List<String> reasons) { }

    /** A stamp shown at the end of a chapter (bank refusal, loan approval…). */
    public record Outcome(boolean approved, String stamp, String headline, String detail) { }

    public record Chapter(String title, String narration, List<Frame> frames, Outcome outcome) { }

    // ids of the small story community
    private static final long SUNIL = 1;
    private static final long MEENA = 2;
    private static final long FAROOQ = 3;
    private static final long LATA = 4;

    private final TrustScoreEngine engine;
    private final LocalDate start;               // the day Sunil joins TrustLend
    private final int minLoanScore;
    private final IntToDoubleFunction interestFor;
    private final Random random = new Random(7);

    private final List<Vendor> vendors = new ArrayList<>();
    private final List<Transaction> sunilTxns = new ArrayList<>();
    private final List<Transaction> otherTxns = new ArrayList<>();
    private final List<Loan> otherLoans = new ArrayList<>();
    private final List<Repayment> otherReps = new ArrayList<>();
    private final List<Vouch> baseVouches = new ArrayList<>();

    private StoryScenario(EngineSettings settings, LocalDate today, int minLoanScore, IntToDoubleFunction interestFor) {
        this.engine = new TrustScoreEngine(settings);
        this.start = today.minusWeeks(30);
        this.minLoanScore = minLoanScore;
        this.interestFor = interestFor;
    }

    /**
     * Builds the five chapters.
     *
     * @param minLoanScore minimum score for any loan (lending policy)
     * @param interestFor  score → annual interest rate in percent (lending policy)
     */
    public static List<Chapter> build(EngineSettings settings, LocalDate today, int minLoanScore,
                                      IntToDoubleFunction interestFor) {
        return new StoryScenario(settings, today, minLoanScore, interestFor).chapters();
    }

    // ------------------------------------------------------------------ the story

    private List<Chapter> chapters() {
        buildCommunity();
        List<Chapter> chapters = new ArrayList<>();
        List<Vouch> vouches = new ArrayList<>(baseVouches);
        List<Loan> sunilLoans = new ArrayList<>();
        List<Repayment> sunilReps = new ArrayList<>();

        // 1. The bank says no
        addSunilSales(start, start.plusDays(5), 1.0);
        LocalDate day = start.plusDays(5);
        Frame first = frame("Day 1 on TrustLend", day, vouches, sunilLoans, sunilReps,
                "Sunil registers his chai stall and records his first week of sales");
        chapters.add(new Chapter("No history, no loan",
                "Sunil has sold chai outside Chandni Chowk metro for 6 years. He needs ₹25,000 for a bigger "
                        + "stall, but the bank turns him away: no salary slip, no credit history. The only option left "
                        + "is a moneylender at about 5% a month. So he joins TrustLend.",
                List.of(first),
                new Outcome(false, "DECLINED", "Bank: \"No credit history\"",
                        "On day 1 TrustLend gives him a starting score of " + first.score()
                                + " — " + (first.score() < minLoanScore ? "not enough for a loan yet." : "a beginning.")))) ;

        // 2. Twelve weeks of steady sales
        List<Frame> weeks = new ArrayList<>();
        for (int w = 1; w <= 12; w++) {
            LocalDate from = start.plusWeeks(w).minusDays(1);
            LocalDate to = start.plusWeeks(w + 1).minusDays(2);
            double before = total(sunilTxns);
            addSunilSales(from, to, 1.0);
            if (w >= 4) sunilTxns.add(new Transaction(SUNIL, 500, Transaction.Type.DEPOSIT, to, "Savings"));
            weeks.add(frame("Week " + w, to, vouches, sunilLoans, sunilReps,
                    "Week " + w + ": sold chai on 6 days, " + Formats.inr(total(sunilTxns) - before)
                            + (w >= 4 ? " · saved ₹500" : "")));
            day = to;
        }
        chapters.add(new Chapter("Every cup counts",
                "Instead of paperwork, TrustLend watches how Sunil actually runs his business. Every day he "
                        + "logs his sales; from week 4 he also saves ₹500 a week. Steady, regular trade is the "
                        + "strongest signal a street vendor can give.",
                weeks, null));

        // 3. Neighbours vouch for him
        List<Frame> vouchFrames = new ArrayList<>();
        Object[][] vouchers = {{MEENA, 5, "Meena, who runs the fruit cart next door"},
                {FAROOQ, 4, "Farooq, his milk supplier for 4 years"},
                {LATA, 5, "Lata, from his market's self-help group"}};
        for (Object[] v : vouchers) {
            day = day.plusDays(1);
            vouches.add(new Vouch(0, (Long) v[0], SUNIL, (Integer) v[1], Vouch.Relationship.NEIGHBOURING_STALL, day));
            addSunilSales(day, day, 1.0);
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY) {
                sunilTxns.add(new Transaction(SUNIL, 500, Transaction.Type.DEPOSIT, day, "Savings"));
            }
            vouchFrames.add(frame("Vouch", day, vouches, sunilLoans, sunilReps,
                    "Vouched for by " + v[2] + " (strength " + v[1] + "/5)"));
        }
        chapters.add(new Chapter("The community vouches",
                "Three trusted vendors put their own reputation on the line for Sunil. Trust flows to him "
                        + "through the network like PageRank: a vouch from someone highly trusted counts for more. "
                        + "If Sunil ever defaults, they lose credibility too.",
                vouchFrames, null));

        // 4. A first small loan, repaid on time
        int scoreAtLoan = vouchFrames.get(vouchFrames.size() - 1).score();
        double rate = interestFor.applyAsDouble(scoreAtLoan);
        LocalDate issue = day.plusDays(1);
        Loan loan = new Loan(100, SUNIL, 5_000, rate, 10, issue, issue.plusWeeks(10), LoanStatus.ACTIVE, scoreAtLoan);
        sunilLoans.add(loan);
        List<Frame> repayFrames = new ArrayList<>();
        for (int n = 1; n <= 10; n++) {
            LocalDate due = loan.instalmentDueDate(n);
            addSunilSales(day.plusDays(1), due, 1.0);
            sunilTxns.add(new Transaction(SUNIL, 500, Transaction.Type.DEPOSIT, due, "Savings"));
            sunilReps.add(new Repayment(0, loan.getId(), loan.getWeeklyInstalment(), due, due.minusDays(1), true));
            if (n == 10) {
                sunilLoans.set(0, new Loan(100, SUNIL, 5_000, rate, 10, issue, issue.plusWeeks(10), LoanStatus.REPAID, scoreAtLoan));
            }
            repayFrames.add(frame("Instalment " + n, due, vouches, sunilLoans, sunilReps,
                    n == 10 ? "Instalment 10 of 10 paid — loan fully repaid!"
                            : "Instalment " + n + " of 10 paid on time (" + Formats.inr(loan.getWeeklyInstalment()) + ")"));
            day = due;
        }
        chapters.add(new Chapter("A first loan, repaid on time",
                "With a score of " + scoreAtLoan + " TrustLend offers Sunil a small starter loan: ₹5,000 for 10 weeks at "
                        + String.format("%.1f%%", rate) + " a year. He pays every single weekly instalment on time.",
                repayFrames, null));

        // 5. Approved
        Frame last = repayFrames.get(repayFrames.size() - 1);
        int finalScore = last.score();
        double finalRate = interestFor.applyAsDouble(finalScore);
        double limit = com.trustlend.model.RiskBand.fromScore(finalScore).getMaxLoan();
        double amount = Math.min(25_000, limit);
        double ours = amount * finalRate / 100 * 0.5;          // simple interest over 26 weeks
        double theirs = amount * MONEYLENDER_RATE / 100 * 0.5;
        boolean ok = finalScore >= minLoanScore && amount > 0;
        chapters.add(new Chapter("Approved",
                "Six months ago Sunil was invisible to lenders. Now he has a track record anyone can verify: "
                        + "steady sales, a repaid loan and three vouches. He applies for the ₹25,000 he needed.",
                List.of(new Frame("Today", finalScore, last.consistency(), last.repayment(), last.tenure(),
                        last.network(), "Applies for " + Formats.inr(25_000) + " for a bigger stall", last.reasons())),
                ok ? new Outcome(true, "APPROVED", Formats.inr(amount) + " at " + String.format("%.1f%%", finalRate) + " p.a.",
                        "Over 6 months he pays " + Formats.inr(ours) + " interest instead of about " + Formats.inr(theirs)
                                + " to a moneylender — saving " + Formats.inr(theirs - ours) + ".")
                        : new Outcome(false, "NOT YET", "Score " + finalScore, "A few more months of steady trade will get him there.")));
        return chapters;
    }

    private Frame frame(String when, LocalDate date, List<Vouch> vouches, List<Loan> sunilLoans,
                        List<Repayment> sunilReps, String event) {
        List<VendorProfile> profiles = new ArrayList<>();
        for (Vendor v : vendors) {
            if (v.getId() == SUNIL) {
                profiles.add(new VendorProfile(v, upTo(sunilTxns, date), sunilLoans, sunilReps));
            } else {
                long id = v.getId();
                profiles.add(new VendorProfile(v,
                        upTo(otherTxns.stream().filter(t -> t.getVendorId() == id).toList(), date),
                        otherLoans.stream().filter(l -> l.getVendorId() == id).toList(),
                        otherReps.stream().filter(r -> r.getLoanId() == id * 10).toList()));
            }
        }
        Map<Long, ScoreBreakdown> scores = engine.computeAll(profiles, vouches, date);
        ScoreBreakdown s = scores.get(SUNIL);
        return new Frame(when, s.score(), s.consistency(), s.repayment(), s.tenure(), s.network(), event, s.reasons());
    }

    // ------------------------------------------------------------------ the story community

    private void buildCommunity() {
        vendors.add(new Vendor(SUNIL, HERO, "+91 90000 00001", VendorCategory.STREET_FOOD, "Chandni Chowk", "Delhi", 6, start, true));
        String[][] others = {
                {"2", "Meena Devi", "FRUITS_VEGETABLES", "9"}, {"3", "Farooq Ali", "KIRANA", "12"},
                {"4", "Lata Joshi", "TAILORING", "8"}, {"5", "Rohit Verma", "MOBILE_REPAIR", "4"},
                {"6", "Pooja Rawat", "FLOWERS", "5"}, {"7", "Imran Khan", "CLOTHING", "3"},
                {"8", "Kiran Bai", "STREET_FOOD", "6"}};
        LocalDate end = start.plusWeeks(31);
        for (String[] o : others) {
            long id = Long.parseLong(o[0]);
            boolean strong = id <= LATA;
            LocalDate joined = start.minusWeeks(strong ? 90 : 40);
            vendors.add(new Vendor(id, o[1], "+91 90000 0000" + id, VendorCategory.valueOf(o[2]), "Chandni Chowk",
                    "Delhi", Integer.parseInt(o[3]), joined, true));
            for (LocalDate d = joined; d.isBefore(end); d = d.plusDays(1)) {
                boolean trades = d.getDayOfWeek() != DayOfWeek.SUNDAY && (strong || random.nextDouble() < 0.7);
                if (trades) {
                    double amount = Math.round((strong ? 2200 : 1400) * (0.85 + 0.3 * random.nextDouble()));
                    otherTxns.add(new Transaction(id, amount, Transaction.Type.SALE, d, "Daily sales"));
                }
                if (strong && d.getDayOfWeek() == DayOfWeek.SATURDAY) {
                    otherTxns.add(new Transaction(id, 800, Transaction.Type.DEPOSIT, d, "Savings"));
                }
            }
            // every story vendor has repaid an earlier loan (strong ones perfectly, others mostly)
            LocalDate issue = joined.plusWeeks(6);
            Loan l = new Loan(id * 10, id, 10_000, 18, 12, issue, issue.plusWeeks(12), LoanStatus.REPAID, 650);
            otherLoans.add(l);
            for (int n = 1; n <= 12; n++) {
                LocalDate due = l.instalmentDueDate(n);
                boolean onTime = strong || n % 4 != 0;
                otherReps.add(new Repayment(0, l.getId(), l.getWeeklyInstalment(), due, onTime ? due : due.plusDays(6), onTime));
            }
        }
        long[][] edges = {{MEENA, FAROOQ}, {FAROOQ, LATA}, {LATA, MEENA}, {FAROOQ, MEENA},
                {5, 6}, {6, 7}, {7, 8}, {8, 5}, {MEENA, 5}, {LATA, 7}};
        for (long[] e : edges) {
            baseVouches.add(new Vouch(0, e[0], e[1], 4, Vouch.Relationship.MARKET_ASSOCIATION, start.minusWeeks(20)));
        }
    }

    private void addSunilSales(LocalDate from, LocalDate to, double scale) {
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            LocalDate day = d;
            if (sunilTxns.stream().anyMatch(t -> t.getDate().equals(day) && t.getType() == Transaction.Type.SALE)) continue;
            double amount = Math.round(1800 * scale * (0.9 + 0.2 * random.nextDouble()));
            sunilTxns.add(new Transaction(SUNIL, amount, Transaction.Type.SALE, d, "Chai sales"));
        }
    }

    private static List<Transaction> upTo(List<Transaction> txns, LocalDate date) {
        return txns.stream().filter(t -> !t.getDate().isAfter(date)).toList();
    }

    private static double total(List<Transaction> txns) {
        return txns.stream().filter(t -> t.getType() == Transaction.Type.SALE).mapToDouble(Transaction::getAmount).sum();
    }
}
