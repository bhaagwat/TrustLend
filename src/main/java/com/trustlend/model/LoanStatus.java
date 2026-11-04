package com.trustlend.model;

public enum LoanStatus {
    ACTIVE("Active"),
    REPAID("Repaid"),
    OVERDUE("Overdue"),
    DEFAULTED("Defaulted");

    private final String label;

    LoanStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    public boolean isOpen() { return this == ACTIVE || this == OVERDUE; }

    @Override
    public String toString() { return label; }
}
