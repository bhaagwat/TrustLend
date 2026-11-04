package com.trustlend.engine.factor;

import com.trustlend.engine.VendorProfile;
import com.trustlend.model.Transaction;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How steady is the vendor's business? Looks at the last N weeks (or fewer for new vendors):
 * <ul>
 *   <li>weekly activity – share of weeks with any transaction            (25%)</li>
 *   <li>daily activity  – share of trading days with a sale (≈6 per week)  (25%)</li>
 *   <li>stability       – 1 / (1 + coefficient of variation of weekly sales, zero weeks included) (40%)</li>
 *   <li>savings habit   – share of weeks with a savings deposit            (10%)</li>
 * </ul>
 * A small but regular tea stall can score as well as a large shop: steadiness matters, not size.
 * For vendors observed for only a few weeks the value is shrunk towards a neutral 0.4,
 * because three good weeks are weaker evidence than twelve.
 */
public class TransactionConsistencyFactor extends AbstractFactor {

    private static final double NEUTRAL = 0.4;
    private final int windowWeeks;

    public TransactionConsistencyFactor(int windowWeeks) {
        super("consistency", "Transaction consistency");
        if (windowWeeks < 2) throw new IllegalArgumentException("window must be at least 2 weeks");
        this.windowWeeks = windowWeeks;
    }

    @Override
    public FactorResult evaluate(VendorProfile profile, LocalDate today) {
        long weeksKnown = ChronoUnit.WEEKS.between(profile.vendor().getJoinDate(), today) + 1;
        int weeks = (int) Math.max(2, Math.min(windowWeeks, weeksKnown));
        LocalDate start = today.minusWeeks(weeks).plusDays(1);

        double[] weeklySales = new double[weeks];
        boolean[] activeWeek = new boolean[weeks];
        boolean[] depositWeek = new boolean[weeks];
        Set<LocalDate> saleDays = new HashSet<>();

        for (Transaction t : profile.transactions()) {
            if (!t.isBusinessActivity() || t.getDate().isBefore(start) || t.getDate().isAfter(today)) {
                continue;
            }
            int week = (int) (ChronoUnit.DAYS.between(start, t.getDate()) / 7);
            if (week < 0 || week >= weeks) continue;
            activeWeek[week] = true;
            switch (t.getType()) {
                case SALE -> {
                    weeklySales[week] += t.getAmount();
                    saleDays.add(t.getDate());
                }
                case DEPOSIT -> depositWeek[week] = true;
                default -> { /* purchases only count as activity */ }
            }
        }

        int active = 0;
        int deposits = 0;
        List<Double> allWeeks = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            if (activeWeek[i]) active++;
            if (depositWeek[i]) deposits++;
            allWeeks.add(weeklySales[i]);
        }

        if (active == 0) {
            return FactorResult.of(0, "No business activity recorded in the last " + weeks + " weeks");
        }

        double weeklyActivity = active / (double) weeks;
        double dailyActivity = Math.min(1.0, saleDays.size() / (weeks * 6.0));
        double stability = 1.0 / (1.0 + coefficientOfVariation(allWeeks));
        double savings = Math.min(1.0, deposits / (double) weeks * 2);
        double raw = 0.25 * weeklyActivity + 0.25 * dailyActivity + 0.4 * stability + 0.1 * savings;

        double confidence = weeks / (double) windowWeeks;
        double value = confidence * raw + (1 - confidence) * NEUTRAL;

        List<String> reasons = new ArrayList<>();
        reasons.add("Sold on " + saleDays.size() + " days across " + active + " of the last " + weeks + " weeks");
        if (stability >= 0.8) {
            reasons.add("Weekly sales are very steady");
        } else if (stability < 0.6) {
            reasons.add("Weekly sales swing a lot from week to week");
        }
        if (deposits > 0) {
            reasons.add("Made savings deposits in " + deposits + " week(s)");
        }
        if (confidence < 1) {
            reasons.add("Only " + weeks + " week(s) of history — consistency estimate is provisional");
        }
        return new FactorResult(value, reasons);
    }

    /** Population standard deviation divided by mean (0 when the mean is 0). */
    public static double coefficientOfVariation(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (mean == 0) return 0;
        double variance = 0;
        for (double v : values) {
            variance += Math.pow(v - mean, 2);
        }
        variance /= values.size();
        return Math.sqrt(variance) / mean;
    }
}
