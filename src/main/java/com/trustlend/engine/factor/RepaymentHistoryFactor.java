package com.trustlend.engine.factor;

import com.trustlend.engine.VendorProfile;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repayment discipline on earlier TrustLend loans.
 *
 * Every payment is weighted by how well it is verified (confirmed by lender and borrower = 1,
 * lender-reported only = 0.5, disputed = 0). Uses a Bayesian average so that a vendor with one
 * on-time payment isn't rated as perfect:
 * <pre>  base = (onTime + 0.5·late + 1) / (payments + 2)     (all counts weighted)  </pre>
 * then subtracts penalties for overdue / defaulted loans and adds a small bonus for fully repaid ones.
 * Vendors with no loans yet start at a neutral 0.5.
 */
public class RepaymentHistoryFactor extends AbstractFactor {

    public RepaymentHistoryFactor() {
        super("repayment", "Repayment history");
    }

    @Override
    public FactorResult evaluate(VendorProfile profile, LocalDate today) {
        List<Repayment> reps = profile.repayments();
        List<Loan> loans = profile.loans();
        if (loans.isEmpty() && reps.isEmpty()) {
            return FactorResult.of(0.5, "No loan history yet, so repayment is rated neutral");
        }

        // Each payment counts by how well it is verified: confirmed by both sides = 1,
        // reported by the lender but not yet confirmed by the borrower = 0.5, disputed = 0.
        double onTime = 0, late = 0, weight = 0;
        int pending = 0, disputed = 0;
        java.util.Map<Long, Double> loanWeight = new java.util.HashMap<>();
        for (Repayment r : reps) {
            double w = r.weight();
            if (r.getStatus() == Repayment.Verification.PENDING) pending++;
            if (w == 0) {
                disputed++;
                continue;
            }
            weight += w;
            if (r.isOnTime()) onTime += w;
            else late += w;
            loanWeight.merge(r.getLoanId(), w, Math::max);
        }
        double base = (onTime + 0.5 * late + 1.0) / (weight + 2.0);

        double repaid = 0;
        int repaidLoans = 0, overdue = 0, defaulted = 0;
        for (Loan l : loans) {
            LoanStatus s = l.getStatus();
            if (s == LoanStatus.REPAID) {
                repaidLoans++;
                repaid += l.getOrigin() == Loan.Origin.REPORTED ? loanWeight.getOrDefault(l.getId(), 0.0) : 1.0;
            } else if (s == LoanStatus.DEFAULTED) defaulted++;
            else if (s == LoanStatus.OVERDUE || (s.isOpen() && today.isAfter(l.getDueDate()))) overdue++;
        }

        double value = base + Math.min(0.10, 0.05 * repaid) - 0.15 * overdue - 0.35 * defaulted;

        List<String> reasons = new ArrayList<>();
        long counted = reps.size() - disputed;
        if (counted > 0) {
            long onTimeCount = reps.stream().filter(r -> r.weight() > 0 && r.isOnTime()).count();
            reasons.add(onTimeCount + " of " + counted + " instalments paid on time (" + pct(onTimeCount / (double) counted) + ")");
        }
        if (repaidLoans > 0) reasons.add(repaidLoans + " loan(s) fully repaid");
        if (pending > 0) reasons.add(pending + " repayment(s) awaiting the borrower's confirmation — counted at half weight");
        if (disputed > 0) reasons.add(disputed + " disputed repayment report(s) — not counted");
        if (overdue > 0) reasons.add(overdue + " loan(s) currently overdue");
        if (defaulted > 0) reasons.add(defaulted + " loan(s) defaulted — strong negative signal");
        return new FactorResult(clamp01(value), reasons);
    }
}
