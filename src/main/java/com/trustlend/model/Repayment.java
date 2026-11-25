package com.trustlend.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** One instalment paid against a loan. */
public class Repayment implements Identifiable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long id;
    private final long loanId;
    private final double amount;
    private final LocalDate dueDate;
    private final LocalDate paidDate;
    private final boolean onTime;
    private Verification status = Verification.CONFIRMED;
    private long reportedBy;        // user id of the lender who reported it (0 = recorded by HQ)
    private String note;            // dispute reason, if any

    /**
     * How sure we are that the payment really happened. A repayment reported by a lender counts
     * fully only after the borrower confirms it: one person alone cannot fake a good record.
     */
    public enum Verification {
        CONFIRMED("Confirmed", 1.0), PENDING("Awaiting borrower", 0.5), DISPUTED("Disputed", 0.0), REJECTED("Rejected", 0.0);

        private final String label;
        private final double weight;

        Verification(String label, double weight) {
            this.label = label;
            this.weight = weight;
        }

        public String getLabel() { return label; }

        /** How much the payment counts in the score: 1 = fully, 0.5 = half, 0 = not at all. */
        public double getWeight() { return weight; }
    }

    public Repayment(long id, long loanId, double amount, LocalDate dueDate, LocalDate paidDate, boolean onTime) {
        this.id = id;
        this.loanId = loanId;
        this.amount = amount;
        this.dueDate = dueDate;
        this.paidDate = paidDate;
        this.onTime = onTime;
    }

    /** Convenience constructor: "on time" means paid on or before the due date (2 days grace). */
    public static Repayment of(long loanId, double amount, LocalDate dueDate, LocalDate paidDate) {
        boolean onTime = !paidDate.isAfter(dueDate.plusDays(2));
        return new Repayment(0, loanId, amount, dueDate, paidDate, onTime);
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }

    public long getLoanId() { return loanId; }
    public double getAmount() { return amount; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getPaidDate() { return paidDate; }
    public boolean isOnTime() { return onTime; }
    public Verification getStatus() { return status; }
    public void setStatus(Verification status) { this.status = status; }
    public long getReportedBy() { return reportedBy; }
    public void setReportedBy(long reportedBy) { this.reportedBy = reportedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    /** Weight of this payment in the repayment factor. */
    public double weight() { return status.getWeight(); }
}
