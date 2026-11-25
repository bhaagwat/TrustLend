package com.trustlend.service;

import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.RiskBand;
import com.trustlend.model.Vendor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Portfolio-level numbers for the dashboard and reports. */
public class AnalyticsService {

    private final VendorService vendorService;
    private final LoanService loanService;
    private final RepaymentDao repaymentDao;
    private final VouchDao vouchDao;

    public AnalyticsService(VendorService vendorService, LoanService loanService,
                            RepaymentDao repaymentDao, VouchDao vouchDao) {
        this.vendorService = vendorService;
        this.loanService = loanService;
        this.repaymentDao = repaymentDao;
        this.vouchDao = vouchDao;
    }

    public record Stats(
            int vendors,
            double averageScore,
            Map<RiskBand, Integer> bandCounts,
            int[] scoreHistogram,          // buckets of 50 points from 300 to 900
            int openLoans,
            double outstanding,
            double totalDisbursed,
            double onTimeRate,
            long vouches,
            int defaulted,
            List<Vendor> topVendors) { }

    public Stats compute() throws DataAccessException {
        List<Vendor> ranked = vendorService.ranked();
        Map<RiskBand, Integer> bands = new EnumMap<>(RiskBand.class);
        for (RiskBand b : RiskBand.values()) bands.put(b, 0);
        int[] histogram = new int[12];
        double total = 0;
        for (Vendor v : ranked) {
            bands.merge(v.getRiskBand(), 1, Integer::sum);
            int bucket = Math.max(0, Math.min(11, (v.getCurrentScore() - 300) / 50));
            histogram[bucket]++;
            total += v.getCurrentScore();
        }

        List<Loan> loans = loanService.listLoans();
        int open = 0, defaulted = 0;
        double outstanding = 0, disbursed = 0;
        for (Loan l : loans) {
            disbursed += l.getPrincipal();
            if (l.getStatus().isOpen()) {
                open++;
                outstanding += l.getOutstanding();
            }
            if (l.getStatus() == LoanStatus.DEFAULTED) defaulted++;
        }

        List<Repayment> reps = repaymentDao.findAll();
        double onTime = reps.isEmpty() ? 0 : reps.stream().filter(Repayment::isOnTime).count() / (double) reps.size();

        return new Stats(ranked.size(), ranked.isEmpty() ? 0 : total / ranked.size(), bands, histogram,
                open, outstanding, disbursed, onTime, vouchDao.count(), defaulted,
                ranked.subList(0, Math.min(5, ranked.size())));
    }

    // ------------------------------------------------------------------ social impact

    /** Typical informal moneylender rate (~5% a month), used to estimate what borrowers saved. */
    public static final double MONEYLENDER_RATE = 60.0;

    /**
     * The good the portfolio has done: money lent, people financed, on-time payments, and the
     * interest saved compared with borrowing the same amounts from a moneylender
     * (simple interest over each loan's tenure, at {@link #MONEYLENDER_RATE} vs the TrustLend rate).
     */
    public record Impact(double lent, int vendorsFinanced, int loans, double interestSaved, long onTimePayments) { }

    public Impact impact() throws DataAccessException {
        List<Loan> loans = loanService.listLoans();
        double lent = 0;
        double saved = 0;
        java.util.Set<Long> borrowers = new java.util.HashSet<>();
        for (Loan l : loans) {
            lent += l.getPrincipal();
            borrowers.add(l.getVendorId());
            double years = l.getTenureWeeks() / 52.0;
            saved += l.getPrincipal() * Math.max(0, MONEYLENDER_RATE - l.getInterestRate()) / 100 * years;
        }
        long onTime = repaymentDao.findAll().stream().filter(Repayment::isOnTime).count();
        return new Impact(lent, borrowers.size(), loans.size(), saved, onTime);
    }

    // ------------------------------------------------------------------ markets

    /** Trust in one market area, for the dashboard heat map. */
    public record MarketStat(String market, int vendors, double averageScore, Map<RiskBand, Integer> bands,
                             String topVendor) { }

    public List<MarketStat> markets() throws DataAccessException {
        Map<String, List<Vendor>> byMarket = new java.util.TreeMap<>();
        for (Vendor v : vendorService.ranked()) {
            byMarket.computeIfAbsent(v.getMarketArea(), k -> new java.util.ArrayList<>()).add(v);
        }
        List<MarketStat> out = new java.util.ArrayList<>();
        byMarket.forEach((market, list) -> {
            Map<RiskBand, Integer> bands = new EnumMap<>(RiskBand.class);
            for (RiskBand b : RiskBand.values()) bands.put(b, 0);
            list.forEach(v -> bands.merge(v.getRiskBand(), 1, Integer::sum));
            double avg = list.stream().mapToInt(Vendor::getCurrentScore).average().orElse(0);
            out.add(new MarketStat(market, list.size(), avg, bands, list.get(0).getName()));   // ranked: best first
        });
        out.sort(java.util.Comparator.comparingDouble(MarketStat::averageScore).reversed());
        return out;
    }
}
