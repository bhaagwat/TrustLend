package com.trustlend.engine.factor;

import com.trustlend.engine.VendorProfile;
import com.trustlend.model.Vendor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Stability: years trading (self-reported), months on the platform, and declared income.
 * Income verified by a lender or HQ adds up to 0.15; self-declared income only 0.05 until verified.
 */
public class TenureFactor extends AbstractFactor {

    public TenureFactor() {
        super("tenure", "Tenure & income");
    }

    @Override
    public FactorResult evaluate(VendorProfile profile, LocalDate today) {
        Vendor v = profile.vendor();
        int months = v.monthsOnPlatform(today);
        double years = Math.min(1.0, v.getYearsInBusiness() / 10.0);
        double platform = Math.min(1.0, months / 24.0);
        double value = 0.6 * years + 0.4 * platform;

        List<String> reasons = new ArrayList<>();
        reasons.add(v.getYearsInBusiness() + " year(s) in business");
        reasons.add(months < 1 ? "Joined TrustLend this month" : months + " month(s) on TrustLend");
        if (profile.verifiedIncome() > 0) {
            value += 0.15;
            reasons.add("Verified income of " + com.trustlend.util.Formats.inr(profile.verifiedIncome()) + " a month");
        } else if (profile.declaredIncome() > 0) {
            value += 0.05;
            reasons.add("Self-declared income of " + com.trustlend.util.Formats.inr(profile.declaredIncome())
                    + " a month (counts more once verified)");
        }
        return new FactorResult(clamp01(value), reasons);
    }
}
