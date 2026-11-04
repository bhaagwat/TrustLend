package com.trustlend.engine;

import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.model.Vouch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustScoreEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private final TrustScoreEngine engine = new TrustScoreEngine(EngineSettings.defaults());

    private static Vendor vendor(long id, int years, int monthsAgo) {
        return new Vendor(id, "Vendor " + id, "98765432" + (10 + id), VendorCategory.TEA_STALL, "Market", "City",
                years, TODAY.minusMonths(monthsAgo), true);
    }

    /** Sales every day for 12 weeks with the given daily amount pattern. */
    private static List<Transaction> dailySales(long vendorId, boolean irregular) {
        List<Transaction> list = new ArrayList<>();
        for (int d = 0; d < 84; d++) {
            LocalDate date = TODAY.minusDays(d);
            int week = d / 7;
            if (irregular && week % 2 == 1) continue;                 // every other week silent
            double amount = irregular ? (week % 4 == 0 ? 3000 : 300) : 1000;
            list.add(new Transaction(vendorId, amount, Transaction.Type.SALE, date, null));
        }
        return list;
    }

    private static Loan loan(long id, long vendorId, LoanStatus status) {
        return new Loan(id, vendorId, 5000, 18, 12, TODAY.minusWeeks(20), TODAY.minusWeeks(8), status, 600);
    }

    @Test
    @DisplayName("Steady daily sales score higher on consistency than irregular sales")
    void consistencyRewardsRegularity() {
        VendorProfile steady = new VendorProfile(vendor(1, 5, 12), dailySales(1, false), List.of(), List.of());
        VendorProfile erratic = new VendorProfile(vendor(2, 5, 12), dailySales(2, true), List.of(), List.of());
        Map<Long, ScoreBreakdown> s = engine.computeAll(List.of(steady, erratic), List.of(), TODAY);
        assertTrue(s.get(1L).consistency() > s.get(2L).consistency() + 0.2);
        assertTrue(s.get(1L).score() > s.get(2L).score());
    }

    @Test
    @DisplayName("All scores stay inside the 300-900 range")
    void scoresAreInRange() {
        VendorProfile empty = new VendorProfile(vendor(1, 0, 0), List.of(), List.of(), List.of());
        VendorProfile good = new VendorProfile(vendor(2, 30, 40), dailySales(2, false), List.of(), List.of());
        for (ScoreBreakdown b : engine.computeAll(List.of(empty, good), List.of(), TODAY).values()) {
            assertTrue(b.score() >= 300 && b.score() <= 900, "score " + b.score());
        }
    }

    @Test
    @DisplayName("A defaulted loan caps the score below 550 even with strong vouches")
    void defaultCapsScore() {
        Vendor v = vendor(1, 10, 24);
        List<Repayment> reps = List.of(Repayment.of(10, 500, TODAY.minusWeeks(19), TODAY.minusWeeks(19)));
        VendorProfile defaulter = new VendorProfile(v, dailySales(1, false), List.of(loan(10, 1, LoanStatus.DEFAULTED)), reps);
        VendorProfile friend = new VendorProfile(vendor(2, 10, 24), dailySales(2, false), List.of(), List.of());
        List<Vouch> vouches = List.of(new Vouch(1, 2, 1, 5, Vouch.Relationship.FAMILY_FRIEND, TODAY));
        ScoreBreakdown b = engine.computeAll(List.of(defaulter, friend), vouches, TODAY).get(1L);
        assertTrue(b.score() < TrustScoreEngine.DEFAULT_CAP);
    }

    @Test
    @DisplayName("Being vouched for raises the network component and the score")
    void vouchesRaiseNetworkTrust() {
        VendorProfile a = new VendorProfile(vendor(1, 8, 20), dailySales(1, false), List.of(), List.of());
        VendorProfile b = new VendorProfile(vendor(2, 2, 2), dailySales(2, false), List.of(), List.of());
        VendorProfile c = new VendorProfile(vendor(3, 2, 2), dailySales(3, false), List.of(), List.of());
        List<Vouch> vouches = List.of(new Vouch(1, 1, 2, 5, Vouch.Relationship.SUPPLIER, TODAY));
        Map<Long, ScoreBreakdown> s = engine.computeAll(List.of(a, b, c), vouches, TODAY);
        assertTrue(s.get(2L).network() > 0);
        assertEquals(0.0, s.get(3L).network(), 1e-9);
        assertTrue(s.get(2L).score() > s.get(3L).score());
    }

    @Test
    @DisplayName("Vouching for a defaulter reduces the voucher's edge weight (credibility)")
    void voucherCredibilityPenalty() {
        VendorProfile voucher = new VendorProfile(vendor(1, 8, 20), List.of(), List.of(), List.of());
        VendorProfile bad = new VendorProfile(vendor(2, 2, 10), List.of(), List.of(loan(5, 2, LoanStatus.DEFAULTED)), List.of());
        VendorProfile other = new VendorProfile(vendor(3, 2, 10), List.of(), List.of(), List.of());
        List<Vouch> vouches = List.of(
                new Vouch(1, 1, 2, 5, Vouch.Relationship.SUPPLIER, TODAY),
                new Vouch(2, 1, 3, 5, Vouch.Relationship.SUPPLIER, TODAY));
        var graph = engine.buildGraph(List.of(voucher, bad, other), vouches);
        assertEquals(1.0 - EngineSettings.defaults().voucherPenalty(), graph.weight(1L, 3L), 1e-9);
    }
}
