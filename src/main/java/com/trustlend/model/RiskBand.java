package com.trustlend.model;

/**
 * Risk bands derived from the 300-900 trust score.
 * Each band carries its own lending limit and UI colour.
 */
public enum RiskBand {
    EXCELLENT("Excellent", 750, 50_000, "#0f9d58"),
    GOOD("Good", 650, 25_000, "#34a853"),
    FAIR("Fair", 550, 12_000, "#f4b400"),
    POOR("Poor", 450, 5_000, "#ef6c00"),
    HIGH_RISK("High Risk", 0, 0, "#d93025");

    private final String label;
    private final int minScore;
    private final double maxLoan;
    private final String colorHex;

    RiskBand(String label, int minScore, double maxLoan, String colorHex) {
        this.label = label;
        this.minScore = minScore;
        this.maxLoan = maxLoan;
        this.colorHex = colorHex;
    }

    /** Static factory: maps a score to its band (bands are declared highest first). */
    public static RiskBand fromScore(int score) {
        for (RiskBand band : values()) {
            if (score >= band.minScore) {
                return band;
            }
        }
        return HIGH_RISK;
    }

    public String getLabel() { return label; }
    public int getMinScore() { return minScore; }
    public double getMaxLoan() { return maxLoan; }
    public String getColorHex() { return colorHex; }

    /** CSS style class used by the UI, e.g. "band-excellent". */
    public String styleClass() {
        return "band-" + name().toLowerCase().replace('_', '-');
    }

    @Override
    public String toString() { return label; }
}
