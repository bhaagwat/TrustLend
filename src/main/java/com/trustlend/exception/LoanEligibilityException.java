package com.trustlend.exception;

/** The vendor cannot receive the requested loan. Carries the maximum amount they could get. */
public class LoanEligibilityException extends TrustLendException {

    private static final long serialVersionUID = 1L;

    private final double maxEligibleAmount;

    public LoanEligibilityException(String message, double maxEligibleAmount) {
        super(message);
        this.maxEligibleAmount = maxEligibleAmount;
    }

    public double getMaxEligibleAmount() {
        return maxEligibleAmount;
    }
}
