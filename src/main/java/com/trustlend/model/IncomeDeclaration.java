package com.trustlend.model;

import java.time.LocalDate;

/** Monthly income a borrower declared (salary or business), optionally verified by a lender or HQ. */
public class IncomeDeclaration implements Identifiable {

    public enum Kind {
        SALARY("Salary"), BUSINESS("Business income"), OTHER("Other income");

        private final String label;

        Kind(String label) { this.label = label; }

        public String getLabel() { return label; }

        @Override public String toString() { return label; }
    }

    public enum Status {
        DECLARED("Self-declared"), VERIFIED("Verified"), REJECTED("Rejected");

        private final String label;

        Status(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private long id;
    private final long vendorId;
    private final double monthlyAmount;
    private final Kind kind;
    private final String proofFile;     // stored copy under data/proofs, may be null
    private final LocalDate declaredOn;
    private Status status;
    private long verifiedBy;

    public IncomeDeclaration(long id, long vendorId, double monthlyAmount, Kind kind, String proofFile,
                             LocalDate declaredOn, Status status, long verifiedBy) {
        this.id = id;
        this.vendorId = vendorId;
        this.monthlyAmount = monthlyAmount;
        this.kind = kind;
        this.proofFile = proofFile;
        this.declaredOn = declaredOn;
        this.status = status;
        this.verifiedBy = verifiedBy;
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }
    public long getVendorId() { return vendorId; }
    public double getMonthlyAmount() { return monthlyAmount; }
    public Kind getKind() { return kind; }
    public String getProofFile() { return proofFile; }
    public LocalDate getDeclaredOn() { return declaredOn; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(long verifiedBy) { this.verifiedBy = verifiedBy; }
}
