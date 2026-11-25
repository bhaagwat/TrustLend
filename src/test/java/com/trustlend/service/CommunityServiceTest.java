package com.trustlend.service;

import com.trustlend.db.DatabaseManager;
import com.trustlend.engine.EngineSettings;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.IncomeDeclaration;
import com.trustlend.model.Loan;
import com.trustlend.model.Repayment;
import com.trustlend.model.Upload;
import com.trustlend.model.User;
import com.trustlend.model.VendorCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The community credit bureau: roles, lender reports + borrower confirmation, uploads, income, HQ. */
class CommunityServiceTest {

    private AppContext ctx;
    private CommunityService c;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager db = DatabaseManager.inMemory();
        db.initSchema();
        ctx = new AppContext(db, EngineSettings.defaults());
        ctx.startUp(true);              // demo community + demo accounts
        c = ctx.community();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private User login(String user, User.Role role) throws Exception {
        return ctx.auth().login(user, user.equals("admin") ? DemoAccounts.HQ_PASSWORD : DemoAccounts.PASSWORD, role);
    }

    @Test
    void demoAccountsExistForEveryRole() throws Exception {
        assertTrue(login("admin", User.Role.ADMIN).isAdmin());
        assertTrue(login("meena_shg", User.Role.LENDER).isLender());
        User javed = login("javed", User.Role.BORROWER);
        assertTrue(javed.getVendorId() > 0);
        // the login page's role must match the account
        ValidationException wrong = assertThrows(ValidationException.class, () -> login("javed", User.Role.LENDER));
        assertTrue(wrong.getMessage().contains("borrower"));
    }

    @Test
    void confirmingALenderReportRaisesTheScoreDisputingDoesNot() throws Exception {
        User javed = login("javed", User.Role.BORROWER);
        long vid = javed.getVendorId();
        List<CommunityService.Claim> claims = c.pendingClaims(vid);
        assertFalse(claims.isEmpty(), "the demo SHG report waits for Javed");
        int before = ctx.scores().scoreValue(vid);
        c.confirm(javed, claims.get(0).repayment().getId());
        assertTrue(ctx.scores().scoreValue(vid) > before, "a confirmed repayment counts fully");

        User meena = login("meena_shg", User.Role.LENDER);
        int beforeReport = ctx.scores().scoreValue(vid);
        // repaid a week before the due date, so it is an on-time payment
        Loan fake = c.reportPastLoan(meena, vid, 9_000, 20, 8, LocalDate.now().minusWeeks(12), LocalDate.now().minusWeeks(5));
        int withPending = ctx.scores().scoreValue(vid);
        assertTrue(withPending >= beforeReport, "an unconfirmed on-time report counts half");
        long id = ctx.repaymentDao().findByLoan(fake.getId()).get(0).getId();
        c.dispute(javed, id, "never happened");
        assertTrue(ctx.scores().scoreValue(vid) <= withPending, "a disputed report stops counting");
        assertEquals(beforeReport, ctx.scores().scoreValue(vid), 1, "back to where it was");
        assertEquals(1, c.queue().disputes().stream().filter(cl -> cl.repayment().getId() == id).count());
    }

    @Test
    void onlyTheBorrowerCanConfirmTheirOwnRecord() throws Exception {
        User javed = login("javed", User.Role.BORROWER);
        User ramesh = login("ramesh", User.Role.BORROWER);
        long claim = c.pendingClaims(javed.getVendorId()).get(0).repayment().getId();
        assertThrows(ValidationException.class, () -> c.confirm(ramesh, claim));
        assertThrows(ValidationException.class, () -> c.confirm(login("meena_shg", User.Role.LENDER), claim));
    }

    @Test
    void lenderCredibilityFollowsConfirmationsAndRejections() throws Exception {
        User hq = login("admin", User.Role.ADMIN);
        User sharma = login("sharma_wholesale", User.Role.LENDER);
        double before = c.lenderStats(sharma).credibility();
        CommunityService.Claim dispute = c.queue().disputes().get(0);      // Kamla's dispute of a Sharma report
        c.resolveDispute(hq, dispute.repayment().getId(), false);
        assertEquals(Repayment.Verification.REJECTED, ctx.repaymentDao().findById(dispute.repayment().getId()).get().getStatus());
        assertTrue(c.lenderStats(sharma).credibility() <= before);
    }

    @Test
    void cleanStatementCountsSuspiciousOneIsHeldAndDuplicatesAreRejected() throws Exception {
        User javed = login("javed", User.Role.BORROWER);
        long vid = javed.getVendorId();
        LocalDate today = LocalDate.now();
        CommunityService.UploadOutcome good = c.uploadStatement(vid, "good.csv", DemoAccounts.statement(today.minusDays(83), 84, 3, false));
        assertEquals(Upload.Status.ACCEPTED, good.upload().getStatus());
        assertTrue(good.scoreAfter() > good.scoreBefore(), "proven sales raise the score");

        CommunityService.UploadOutcome again = c.uploadStatement(vid, "good.csv", DemoAccounts.statement(today.minusDays(83), 84, 3, false));
        assertEquals(Upload.Status.REJECTED, again.upload().getStatus());

        CommunityService.UploadOutcome bad = c.uploadStatement(login("ramesh", User.Role.BORROWER).getVendorId(), "bad.csv",
                DemoAccounts.statement(today.minusDays(20), 20, 9, true));
        assertTrue(bad.upload().getStatus() != Upload.Status.ACCEPTED, "round amounts and copied rows are caught");
        assertEquals(bad.scoreBefore(), bad.scoreAfter(), "held data is not scored");
    }

    @Test
    void verifiedIncomeCountsMoreThanDeclared() throws Exception {
        User javed = login("javed", User.Role.BORROWER);
        long vid = javed.getVendorId();
        int base = ctx.scores().scoreValue(vid);
        IncomeDeclaration d = c.declareIncome(vid, 15_000, IncomeDeclaration.Kind.BUSINESS, null);
        int declared = ctx.scores().scoreValue(vid);
        c.verifyIncome(login("udaan_mfi", User.Role.LENDER), d.getId(), true);
        int verified = ctx.scores().scoreValue(vid);
        assertTrue(declared >= base && verified > declared, base + " → " + declared + " → " + verified);
        assertThrows(ValidationException.class, () -> c.verifyIncome(javed, d.getId(), true));   // not your own
    }

    @Test
    void newBorrowerAndLenderCanSignUp() throws Exception {
        User b = c.signUpBorrower("Asha Kumari", "9876501234", VendorCategory.TEA_STALL, "Karol Bagh", "Delhi", 2,
                "asha", "chai2026", "chai2026");
        assertTrue(b.isBorrower() && b.getVendorId() > 0);
        assertTrue(c.findBorrower(CommunityService.trustId(b.getVendorId())).isPresent());
        assertTrue(c.findBorrower("98765 01234").isPresent());
        User l = c.signUpLender("Ravi Gupta", "Gupta Kirana Credit", "Wholesaler (stock on credit)", "gupta", "lend2026", "lend2026");
        assertTrue(l.isLender());
        assertEquals(50, c.lenderStats(l).credibilityPercent());     // a new lender starts in the middle
        assertThrows(ValidationException.class, () -> c.signUpLender("X", "Y", "Moneylender", "gupta", "lend2026", "lend2026"));
    }
}
