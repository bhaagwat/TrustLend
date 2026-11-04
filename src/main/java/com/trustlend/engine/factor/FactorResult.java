package com.trustlend.engine.factor;

import java.util.List;

/** Output of one {@link ScoringFactor}: a normalised value and the reasons behind it. */
public record FactorResult(double value, List<String> reasons) {

    public FactorResult {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Factor value is NaN");
        }
        value = Math.max(0, Math.min(1, value));
        reasons = List.copyOf(reasons);
    }

    public static FactorResult of(double value, String... reasons) {
        return new FactorResult(value, List.of(reasons));
    }
}
