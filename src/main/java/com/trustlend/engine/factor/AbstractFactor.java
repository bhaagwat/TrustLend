package com.trustlend.engine.factor;

/** Shared helpers for factor implementations. */
public abstract class AbstractFactor implements ScoringFactor {

    private final String key;
    private final String displayName;

    protected AbstractFactor(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    @Override
    public final String key() {
        return key;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    protected static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    protected static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
