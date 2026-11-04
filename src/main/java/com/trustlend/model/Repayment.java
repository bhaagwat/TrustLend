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
}
