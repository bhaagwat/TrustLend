package com.trustlend.model;

import java.time.LocalDate;

/**
 * A sales or UPI statement file uploaded by a borrower, with the result of the automatic checks.
 * {@code quality} is 0-100; the file is accepted, held for HQ review, or rejected accordingly.
 */
public class Upload implements Identifiable {

    public enum Status {
        ACCEPTED("Accepted"), REVIEW("Held for review"), REJECTED("Rejected");

        private final String label;

        Status(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private long id;
    private final long vendorId;
    private final String fileName;
    private final LocalDate uploadedOn;
    private final int rows;
    private final int salesDays;
    private final double totalSales;
    private final int quality;
    private Status status;
    private final String issues;     // one issue per line

    public Upload(long id, long vendorId, String fileName, LocalDate uploadedOn, int rows, int salesDays,
                  double totalSales, int quality, Status status, String issues) {
        this.id = id;
        this.vendorId = vendorId;
        this.fileName = fileName;
        this.uploadedOn = uploadedOn;
        this.rows = rows;
        this.salesDays = salesDays;
        this.totalSales = totalSales;
        this.quality = quality;
        this.status = status;
        this.issues = issues;
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }
    public long getVendorId() { return vendorId; }
    public String getFileName() { return fileName; }
    public LocalDate getUploadedOn() { return uploadedOn; }
    public int getRows() { return rows; }
    public int getSalesDays() { return salesDays; }
    public double getTotalSales() { return totalSales; }
    public int getQuality() { return quality; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getIssues() { return issues == null ? "" : issues; }
}
