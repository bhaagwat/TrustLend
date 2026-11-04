package com.trustlend.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable result of scoring one vendor. Every factor is in [0, 1];
 * {@code reasons} explains the score in plain language (explainable credit scoring).
 *
 * Records (Java 16+) give us constructor, accessors, equals/hashCode and toString for free.
 */
public record ScoreBreakdown(
        long vendorId,
        int score,
        double consistency,
        double repayment,
        double tenure,
        double network,
        List<String> reasons,
        LocalDate computedOn) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Compact canonical constructor: validates and defensively copies. */
    public ScoreBreakdown {
        if (score < 0) throw new IllegalArgumentException("score must be >= 0");
        reasons = List.copyOf(reasons);
    }

    public RiskBand band() {
        return RiskBand.fromScore(score);
    }
}
