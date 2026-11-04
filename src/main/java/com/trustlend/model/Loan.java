package com.trustlend.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A micro-loan issued to a vendor. Simple flat interest, repaid in weekly instalments. */
public class Loan implements Identifiable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long id;
    private long vendorId;
    private double principal;
    private double interestRate;      // annual, percent
    private int tenureWeeks;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LoanStatus status;
    private int scoreAtIssue;

    /** Filled by the service layer for display. */
    private transient double amountRepaid;
    private transient String vendorName;

    public Loan(long id, long vendorId, double principal, double interestRate, int tenureWeeks,
                LocalDate issueDate, LocalDate dueDate, LoanStatus status, int scoreAtIssue) {
        this.id = id;
        this.vendorId = vendorId;
        this.principal = principal;
        this.interestRate = interestRate;
        this.tenureWeeks = tenureWeeks;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.status = status;
        this.scoreAtIssue = scoreAtIssue;
    }

    /** Total payable = principal + flat interest for the tenure. */
    public double getTotalPayable() {
        double years = tenureWeeks / 52.0;
        return Math.round(principal * (1 + interestRate / 100.0 * years) * 100.0) / 100.0;
    }

    public double getWeeklyInstalment() {
        return Math.ceil(getTotalPayable() / tenureWeeks);
    }

    /** Due date of the n-th weekly instalment (1-based). */
    public LocalDate instalmentDueDate(int n) {
        return issueDate.plusWeeks(n);
    }

    public double getOutstanding() {
        return Math.max(0, getTotalPayable() - amountRepaid);
    }

    public double getProgress() {
        double total = getTotalPayable();
        return total == 0 ? 0 : Math.min(1.0, amountRepaid / total);
    }

    public long daysPastDue(LocalDate today) {
        return status.isOpen() && today.isAfter(dueDate) ? ChronoUnit.DAYS.between(dueDate, today) : 0;
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }

    public long getVendorId() { return vendorId; }
    public double getPrincipal() { return principal; }
    public double getInterestRate() { return interestRate; }
    public int getTenureWeeks() { return tenureWeeks; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }
    public int getScoreAtIssue() { return scoreAtIssue; }

    public double getAmountRepaid() { return amountRepaid; }
    public void setAmountRepaid(double amountRepaid) { this.amountRepaid = amountRepaid; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
}
