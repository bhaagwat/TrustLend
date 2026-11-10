package com.trustlend.service;

import com.trustlend.db.DatabaseManager;
import com.trustlend.engine.EngineSettings;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Vendor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story mode, what-if projections and loan decisions. */
class DemoFeaturesTest {

    private AppContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager db = DatabaseManager.inMemory();
        db.initSchema();
        ctx = new AppContext(db, EngineSettings.defaults());
        ctx.loadDemoData();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private Vendor lowest() throws Exception {
        return ctx.vendors().findAll().stream().filter(v -> !v.getName().isEmpty())
                .min(Comparator.comparingInt(Vendor::getCurrentScore)).orElseThrow();
    }

    private Vendor highest() throws Exception {
        return ctx.vendors().findAll().stream().max(Comparator.comparingInt(Vendor::getCurrentScore)).orElseThrow();
    }

    @Test
    void storyRisesFromNoLoanToApproval() {
        List<StoryScenario.Chapter> chapters = StoryScenario.build(EngineSettings.defaults(), LocalDate.now(), 500,
                s -> ctx.loans().interestFor(s));
        assertEquals(5, chapters.size());
        int first = chapters.get(0).frames().get(0).score();
        StoryScenario.Chapter last = chapters.get(4);
        int end = last.frames().get(0).score();
        assertTrue(first < 500, "starts without a loan: " + first);
        assertTrue(end > first + 150, "ends much higher: " + end);
        assertFalse(chapters.get(0).outcome().approved());
        assertTrue(last.outcome().approved());
        // the vouch chapter must raise the network factor
        List<StoryScenario.Frame> vouches = chapters.get(2).frames();
        assertTrue(vouches.get(vouches.size() - 1).network() > 0.2);
    }

    @Test
    void positiveScenarioRaisesAndDefaultLowersTheScore() throws Exception {
        Vendor v = lowest();
        ScoreService.Projection good = ctx.scores().project(v.getId(), new ScoreService.Scenario(8, 6, 3, false));
        assertTrue(good.delta() > 0, "delta " + good.delta());
        assertTrue(good.assumptions().size() >= 3);   // plus "takes a new loan" if none is open

        Vendor top = highest();
        ScoreService.Projection bad = ctx.scores().project(top.getId(), new ScoreService.Scenario(0, 0, 0, true));
        assertTrue(bad.projected().score() < 550, "a default caps the score: " + bad.projected().score());

        ScoreService.Projection none = ctx.scores().project(top.getId(), ScoreService.Scenario.NONE);
        assertEquals(0, none.delta());
        assertEquals(top.getCurrentScore(), ctx.scores().scoreValue(top.getId()));   // nothing was saved
    }

    @Test
    void loanDecisionApprovesReducesOrDeclines() throws Exception {
        Vendor eligible = ctx.vendors().findAll().stream()
                .filter(v -> {
                    try {
                        return ctx.loans().offerFor(v.getId()).maxAmount() > 0;
                    } catch (Exception e) {
                        return false;
                    }
                }).findFirst().orElseThrow();
        double limit = ctx.loans().offerFor(eligible.getId()).maxAmount();
        LocalDate today = LocalDate.now();

        LoanService.Decision ok = ctx.loans().decide(eligible.getId(), limit / 2, 12, today);
        assertEquals(LoanService.Verdict.APPROVED, ok.verdict());
        assertEquals(12, ok.schedule().size());
        assertEquals(0.0, ok.schedule().get(11).balanceAfter(), 0.001);
        assertTrue(ok.totalInterest() > 0);

        LoanService.Decision reduced = ctx.loans().decide(eligible.getId(), limit * 3, 12, today);
        assertEquals(LoanService.Verdict.REDUCED, reduced.verdict());
        assertEquals(limit, reduced.approvedAmount(), 0.001);

        long defaulter = ctx.loans().listLoans().stream().filter(l -> l.getStatus() == LoanStatus.DEFAULTED)
                .findFirst().orElseThrow().getVendorId();
        LoanService.Decision no = ctx.loans().decide(defaulter, 5000, 12, today);
        assertEquals(LoanService.Verdict.DECLINED, no.verdict());
        assertTrue(no.schedule().isEmpty());
    }

    @Test
    void scheduleAddsUpToTotalPayable() {
        List<LoanService.Instalment> rows = LoanService.schedule(10_000, 18, 10, LocalDate.of(2026, 1, 1));
        double sum = rows.stream().mapToDouble(LoanService.Instalment::amount).sum();
        assertEquals(10_000 * (1 + 0.18 * 10 / 52.0), sum, 0.05);
        assertEquals(LocalDate.of(2026, 1, 8), rows.get(0).dueDate());
    }
}
