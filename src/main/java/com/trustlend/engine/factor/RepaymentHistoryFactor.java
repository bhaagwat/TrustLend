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
 * Uses a Bayesian average so that a vendor with one on-time payment isn't rated as perfect:
 * <pre>  base = (onTime + 0.5·late + 1) / (payments + 2)  </pre>
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

        long onTime = reps.stream().filter(Repayment::isOnTime).count();
        long late = reps.size() - onTime;
        double base = (onTime + 0.5 * late + 1.0) / (reps.size() + 2.0);

        int repaid = 0, overdue = 0, defaulted = 0;
        for (Loan l : loans) {
            LoanStatus s = l.getStatus();
            if (s == LoanStatus.REPAID) repaid++;
            else if (s == LoanStatus.DEFAULTED) defaulted++;
            else if (s == LoanStatus.OVERDUE || (s.isOpen() && today.isAfter(l.getDueDate()))) overdue++;
        }

        double value = base + Math.min(0.10, 0.05 * repaid) - 0.15 * overdue - 0.35 * defaulted;

        List<String> reasons = new ArrayList<>();
        if (!reps.isEmpty()) {
            reasons.add(onTime + " of " + reps.size() + " instalments paid on time (" + pct(onTime / (double) reps.size()) + ")");
        }
        if (repaid > 0) reasons.add(repaid + " loan(s) fully repaid");
        if (overdue > 0) reasons.add(overdue + " loan(s) currently overdue");
        if (defaulted > 0) reasons.add(defaulted + " loan(s) defaulted — strong negative signal");
        return new FactorResult(clamp01(value), reasons);
    }
}
