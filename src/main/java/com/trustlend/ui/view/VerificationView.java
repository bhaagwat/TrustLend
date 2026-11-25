package com.trustlend.ui.view;

import com.trustlend.model.IncomeDeclaration;
import com.trustlend.model.Upload;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * TrustLend HQ: the few things the system cannot decide on its own — disputed repayment reports,
 * statements that looked suspicious, and income declarations waiting for a check.
 */
public class VerificationView extends BaseView {

    private final VBox disputes = new VBox(10);
    private final VBox uploads = new VBox(10);
    private final VBox incomes = new VBox(10);
    private final Label summary = Ui.label("", "page-subtitle");

    public VerificationView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        setHeader("Verification", "Everything else is automatic. These need a human decision.");
        VBox content = new VBox(16, summary,
                Ui.titledCard("Disputed repayment reports", disputes),
                Ui.titledCard("Statements held for review (failed some fraud checks)", uploads),
                Ui.titledCard("Income declarations to verify", incomes));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    @Override
    public void refresh() {
        try {
            CommunityService.Queue q = ctx.community().queue();
            summary.setText(q.size() == 0 ? "All clear — nothing waiting." : q.size() + " item(s) waiting for a decision");
            Map<Long, Vendor> vendors = new HashMap<>();
            ctx.vendors().findAll().forEach(v -> vendors.put(v.getId(), v));

            disputes.getChildren().clear();
            for (CommunityService.Claim c : q.disputes()) {
                String text = c.lenderName() + " reported that " + c.loan().getVendorName() + " paid "
                        + Formats.inr(c.repayment().getAmount()) + " on " + Formats.date(c.repayment().getPaidDate())
                        + ".\nBorrower: " + (c.repayment().getNote() == null ? "disputes it" : c.repayment().getNote());
                disputes.getChildren().add(item(text,
                        button("Payment happened", "btn-primary", () -> ctx.community().resolveDispute(shell.user(), c.repayment().getId(), true)),
                        button("Reject the report", "btn-danger", () -> ctx.community().resolveDispute(shell.user(), c.repayment().getId(), false))));
            }
            empty(disputes);

            uploads.getChildren().clear();
            for (Upload u : q.heldUploads()) {
                Vendor v = vendors.get(u.getVendorId());
                String text = (v == null ? "?" : v.getName()) + " — " + u.getFileName() + " · quality " + u.getQuality() + "/100 · "
                        + u.getSalesDays() + " day(s), " + Formats.inr(u.getTotalSales()) + "\n⚠ " + u.getIssues().replace("\n", "\n⚠ ");
                uploads.getChildren().add(item(text,
                        button("Approve", "btn-primary", () -> ctx.community().approveUpload(shell.user(), u.getId())),
                        button("Reject", "btn-danger", () -> ctx.community().rejectUpload(shell.user(), u.getId()))));
            }
            empty(uploads);

            incomes.getChildren().clear();
            for (IncomeDeclaration d : q.incomes()) {
                Vendor v = vendors.get(d.getVendorId());
                String text = (v == null ? "?" : v.getName()) + " declared " + Formats.inr(d.getMonthlyAmount()) + " a month ("
                        + d.getKind().getLabel().toLowerCase() + ") on " + Formats.date(d.getDeclaredOn())
                        + (d.getProofFile() == null ? " · no proof attached" : " · proof attached");
                HBox row = item(text,
                        button("Verify", "btn-primary", () -> ctx.community().verifyIncome(shell.user(), d.getId(), true)),
                        button("Reject", "btn-danger", () -> ctx.community().verifyIncome(shell.user(), d.getId(), false)));
                if (d.getProofFile() != null) {
                    Button open = new Button("Open proof");
                    open.getStyleClass().add("btn-ghost");
                    open.setOnAction(e -> Ui.openFile(CommunityService.proofPath(d)));
                    row.getChildren().add(2, open);
                }
                incomes.getChildren().add(row);
            }
            empty(incomes);
        } catch (Exception e) {
            Ui.error("Could not load the verification queue", e);
        }
    }

    private interface Action {
        void run() throws Exception;
    }

    private Button button(String text, String style, Action action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(e -> {
            try {
                action.run();
                shell.refreshAll();
                shell.toast("Done — scores updated");
            } catch (Exception ex) {
                Ui.error("Could not complete", ex);
            }
        });
        return b;
    }

    private static HBox item(String text, Button... buttons) {
        Label t = Ui.label(text, "small");
        t.setWrapText(true);
        t.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(t, Priority.ALWAYS);
        HBox row = new HBox(10, t);
        row.getChildren().addAll(buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("queue-item");
        return row;
    }

    private static void empty(VBox box) {
        if (box.getChildren().isEmpty()) box.getChildren().add(Ui.label("Nothing waiting", "muted"));
    }
}
