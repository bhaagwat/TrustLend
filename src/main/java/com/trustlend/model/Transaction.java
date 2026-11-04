package com.trustlend.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/** One business transaction recorded for a vendor (daily sales, stock purchase, savings deposit...). */
public class Transaction implements Identifiable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        SALE("Sale"), PURCHASE("Stock purchase"), DEPOSIT("Savings deposit"), LOAN_DISBURSAL("Loan disbursal");

        private final String label;
        Type(String label) { this.label = label; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    private long id;
    private long vendorId;
    private double amount;
    private Type type;
    private LocalDate date;
    private String note;

    public Transaction(long id, long vendorId, double amount, Type type, LocalDate date, String note) {
        this.id = id;
        this.vendorId = vendorId;
        this.amount = amount;
        this.type = type;
        this.date = date;
        this.note = note;
    }

    public Transaction(long vendorId, double amount, Type type, LocalDate date, String note) {
        this(0, vendorId, amount, type, date, note);
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }

    public long getVendorId() { return vendorId; }
    public double getAmount() { return amount; }
    public Type getType() { return type; }
    public LocalDate getDate() { return date; }
    public String getNote() { return note; }

    public void setAmount(double amount) { this.amount = amount; }
    public void setType(Type type) { this.type = type; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setNote(String note) { this.note = note; }

    /** Counts towards business activity (excludes money the platform itself lent). */
    public boolean isBusinessActivity() {
        return type != Type.LOAN_DISBURSAL;
    }

    @Override
    public String toString() {
        return String.format("%s ₹%.2f on %s", type, amount, date);
    }
}
