package com.trustlend.ui.component;

import com.trustlend.model.IncomeDeclaration;
import com.trustlend.model.User;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "What is this score built from?" — every kind of evidence behind a borrower's score and how much
 * it counts: confirmed by both sides (full), reported by one side (half), held or disputed (not yet).
 * A lender or HQ looking at the panel can verify a self-declared income right here.
 */
public class EvidencePanel extends VBox {

    private final AppContext ctx;
    private final long vendorId;
    private final User viewer;
    private final Runnable onChange;
    private final VBox rows = new VBox(8);

    public EvidencePanel(AppContext ctx, long vendorId, User viewer, Runnable onChange) {
        super(10);
        this.ctx = ctx;
        this.vendorId = vendorId;
        this.viewer = viewer;
        this.onChange = onChange;
        getStyleClass().add("card");
        Label sub = Ui.label("How sure we are about each piece of data behind the score.", "muted", "small");
        getChildren().addAll(Ui.label("Evidence behind this score", "section-title"), sub, rows);
    }

    public void refresh() {
        rows.getChildren().clear();
        try {
            CommunityService.Evidence e = ctx.community().evidence(vendorId);
            add(e.uploadedSaleDays(), "check", e.uploadedSaleDays() + " day(s) of sales from uploaded UPI statements", "Counts fully", "full");
            add(e.recordedSaleDays(), "check", e.recordedSaleDays() + " day(s) of sales recorded by lenders / HQ", "Counts fully", "full");
            add(e.heldSaleDays(), "cross", e.heldSaleDays() + " day(s) from a suspicious file, held for HQ review", "Not counted yet", "none");
            add(e.confirmedRepayments(), "check", e.confirmedRepayments() + " repayment(s) confirmed by lender and borrower", "Counts fully", "full");
            add(e.pendingRepayments(), "help", e.pendingRepayments() + " repayment(s) reported by a lender, awaiting the borrower", "Counts half", "half");
            add(e.disputedRepayments(), "cross", e.disputedRepayments() + " disputed or rejected repayment report(s)", "Not counted", "none");
            add(e.reportedLoans(), "loans", e.reportedLoans() + " outside loan(s) reported by lenders", "History", "info");
            add(e.vouches(), "vendors", e.vouches() + " vouch(es) from community members", "Weighted by their trust", "info");
            if (e.verifiedIncome() > 0) {
                add(1, "check", "Verified income of " + Formats.inr(e.verifiedIncome()) + " a month", "Counts fully", "full");
            }
            if (e.declaredIncome() > 0) {
                HBox row = row("help", "Self-declared income of " + Formats.inr(e.declaredIncome()) + " a month",
                        "Counts a little until verified", "half");
                if (viewer != null && !viewer.isBorrower()) {
                    for (IncomeDeclaration d : ctx.community().incomesOf(vendorId)) {
                        if (d.getStatus() != IncomeDeclaration.Status.DECLARED) continue;
                        Button verify = new Button("Verify");
                        verify.getStyleClass().add("btn-primary");
                        verify.setOnAction(ev -> verify(d.getId()));
                        row.getChildren().add(verify);
                        break;
                    }
                }
                rows.getChildren().add(row);
            }
            if (rows.getChildren().isEmpty()) {
                rows.getChildren().add(Ui.label("No evidence yet — upload a UPI statement or ask a lender to report a repayment.", "muted"));
            }
        } catch (Exception ex) {
            rows.getChildren().add(Ui.label("Could not load evidence: " + ex.getMessage(), "error-text"));
        }
    }

    private void add(int count, String icon, String text, String tag, String level) {
        if (count > 0) rows.getChildren().add(row(icon, text, tag, level));
    }

    private static HBox row(String icon, String text, String tag, String level) {
        Label t = Ui.label(text, "evidence-text");
        t.setWrapText(true);
        HBox.setHgrow(t, Priority.ALWAYS);
        Label pill = Ui.label(tag, "pill", "evidence-" + level);
        pill.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, Icons.of(icon, 16), t, pill);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("evidence-row", "evidence-row-" + level);
        return row;
    }

    private void verify(long incomeId) {
        if (!Ui.confirm("Verify income", "Have you checked this borrower's salary slip or bank statement? "
                + "Verified income counts fully towards their score.", "Yes, verify")) return;
        try {
            ctx.community().verifyIncome(viewer, incomeId, true);
            refresh();
            if (onChange != null) onChange.run();
        } catch (Exception ex) {
            Ui.error("Could not verify", ex);
        }
    }
}
