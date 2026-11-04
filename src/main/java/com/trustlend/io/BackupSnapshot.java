package com.trustlend.io;

import com.trustlend.model.Loan;
import com.trustlend.model.Repayment;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.Vouch;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** The whole business dataset as one serializable object graph (Unit 3: serialization). */
public class BackupSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final LocalDateTime createdAt = LocalDateTime.now();
    private final String appVersion;
    private final ArrayList<Vendor> vendors;
    private final ArrayList<Transaction> transactions;
    private final ArrayList<Loan> loans;
    private final ArrayList<Repayment> repayments;
    private final ArrayList<Vouch> vouches;

    public BackupSnapshot(String appVersion, List<Vendor> vendors, List<Transaction> transactions,
                          List<Loan> loans, List<Repayment> repayments, List<Vouch> vouches) {
        this.appVersion = appVersion;
        this.vendors = new ArrayList<>(vendors);
        this.transactions = new ArrayList<>(transactions);
        this.loans = new ArrayList<>(loans);
        this.repayments = new ArrayList<>(repayments);
        this.vouches = new ArrayList<>(vouches);
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getAppVersion() { return appVersion; }
    public List<Vendor> getVendors() { return vendors; }
    public List<Transaction> getTransactions() { return transactions; }
    public List<Loan> getLoans() { return loans; }
    public List<Repayment> getRepayments() { return repayments; }
    public List<Vouch> getVouches() { return vouches; }

    public String summary() {
        return vendors.size() + " vendors, " + transactions.size() + " transactions, " + loans.size()
                + " loans, " + repayments.size() + " repayments, " + vouches.size() + " vouches";
    }
}
