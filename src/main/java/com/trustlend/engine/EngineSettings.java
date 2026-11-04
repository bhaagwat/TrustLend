package com.trustlend.engine;

import com.trustlend.config.AppConfig;

/** Tunable parameters of the scoring engine, normally read from config.properties. */
public record EngineSettings(
        int minScore,
        int maxScore,
        double wConsistency,
        double wRepayment,
        double wTenure,
        double wNetwork,
        double damping,
        int iterations,
        double tolerance,
        double voucherPenalty,
        int consistencyWindowWeeks) {

    public EngineSettings {
        if (maxScore <= minScore) throw new IllegalArgumentException("maxScore must exceed minScore");
        double sum = wConsistency + wRepayment + wTenure + wNetwork;
        if (sum <= 0) throw new IllegalArgumentException("At least one weight must be positive");
        // Normalise weights so they always add up to exactly 1
        wConsistency /= sum;
        wRepayment /= sum;
        wTenure /= sum;
        wNetwork /= sum;
    }

    public static EngineSettings fromConfig(AppConfig c) {
        return new EngineSettings(
                c.getInt("score.min"), c.getInt("score.max"),
                c.getDouble("weight.consistency"), c.getDouble("weight.repayment"),
                c.getDouble("weight.tenure"), c.getDouble("weight.network"),
                c.getDouble("graph.damping"), c.getInt("graph.iterations"),
                c.getDouble("graph.tolerance"), c.getDouble("graph.voucher.penalty"),
                c.getInt("consistency.window.weeks"));
    }

    public static EngineSettings defaults() {
        return new EngineSettings(300, 900, 0.30, 0.35, 0.10, 0.25, 0.70, 40, 1e-6, 0.15, 12);
    }

    public double weightFor(String key) {
        return switch (key) {
            case "consistency" -> wConsistency;
            case "repayment" -> wRepayment;
            case "tenure" -> wTenure;
            case "network" -> wNetwork;
            default -> throw new IllegalArgumentException("Unknown factor " + key);
        };
    }
}
