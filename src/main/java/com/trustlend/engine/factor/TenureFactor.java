package com.trustlend.engine.factor;

import com.trustlend.engine.VendorProfile;
import com.trustlend.model.Vendor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Stability of the business itself: years trading (self-reported) and months on the platform. */
public class TenureFactor extends AbstractFactor {

    public TenureFactor() {
        super("tenure", "Business tenure");
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
        return new FactorResult(value, reasons);
    }
}
