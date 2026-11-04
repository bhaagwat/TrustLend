package com.trustlend.engine;

import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the scoring engine needs to know about one vendor, already loaded from the DB.
 * Keeping the engine free of JDBC makes it easy to unit-test.
 */
public record VendorProfile(Vendor vendor,
                            List<Transaction> transactions,
                            List<Loan> loans,
                            List<Repayment> repayments) {

    public VendorProfile {
        transactions = List.copyOf(transactions);
        loans = List.copyOf(loans);
        repayments = List.copyOf(repayments);
    }

    public long vendorId() {
        return vendor.getId();
    }

    public boolean hasDefaulted() {
        return loans.stream().anyMatch(l -> l.getStatus() == LoanStatus.DEFAULTED);
    }

    /**
     * The same profile "as it looked" on an earlier date: later transactions, loans and
     * repayments are dropped, and a default that happened after that date is shown as still active.
     * Used to back-fill score history for the charts.
     */
    public VendorProfile asOf(LocalDate date) {
        List<Transaction> txns = new ArrayList<>();
        for (Transaction t : transactions) {
            if (!t.getDate().isAfter(date)) txns.add(t);
        }
        List<Loan> pastLoans = new ArrayList<>();
        Set<Long> loanIds = new HashSet<>();
        for (Loan l : loans) {
            if (l.getIssueDate().isAfter(date)) continue;
            LoanStatus status = l.getStatus();
            if ((status == LoanStatus.DEFAULTED || status == LoanStatus.OVERDUE) && l.getDueDate().isAfter(date)) {
                status = LoanStatus.ACTIVE;
            }
            Loan copy = new Loan(l.getId(), l.getVendorId(), l.getPrincipal(), l.getInterestRate(),
                    l.getTenureWeeks(), l.getIssueDate(), l.getDueDate(), status, l.getScoreAtIssue());
            pastLoans.add(copy);
            loanIds.add(l.getId());
        }
        List<Repayment> reps = new ArrayList<>();
        for (Repayment r : repayments) {
            if (!r.getPaidDate().isAfter(date) && loanIds.contains(r.getLoanId())) reps.add(r);
        }
        return new VendorProfile(vendor, txns, pastLoans, reps);
    }
}
