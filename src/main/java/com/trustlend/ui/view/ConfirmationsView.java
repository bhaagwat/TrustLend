package com.trustlend.ui.view;

import com.trustlend.model.Loan;
import com.trustlend.model.Repayment;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Avatar;
import com.trustlend.ui.component.Icons;
import com.trustlend.util.Formats;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Borrower: repayments that lenders reported about you. Confirming makes them count fully;
 * disputing sends them to TrustLend HQ and they stop counting. One person alone can never
 * build — or ruin — a credit record.
 */
public class ConfirmationsView extends BaseView {

    private final VBox pending = new VBox(12);
    private final VBox history = new VBox(8);

    public ConfirmationsView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        setHeader("Confirmations", "Your lenders report the payments you made. Confirm the true ones — "
                + "they then count fully. Dispute anything that did not happen.");
        Label how = Ui.label("Why? A lender alone could invent payments to help a friend, or a borrower could claim "
                + "payments they never made. A record counts fully only when both sides agree.", "muted", "small");
        how.setWrapText(true);
        VBox content = new VBox(16, how, Ui.titledCard("Waiting for you", pending), Ui.titledCard("Already answered", history));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    @Override
    public void refresh() {
        long vendorId = shell.user().getVendorId();
        try {
            List<CommunityService.Claim> claims = ctx.community().pendingClaims(vendorId);
            pending.getChildren().clear();
            if (claims.isEmpty()) {
                pending.getChildren().add(Ui.label("Nothing to confirm — you're all caught up.", "muted"));
            }
            for (CommunityService.Claim c : claims) pending.getChildren().add(card(c));

            history.getChildren().clear();
            for (Repayment r : ctx.loans().loansOf(vendorId).stream()
                    .flatMap(l -> {
                        try {
                            return ctx.loans().repaymentsOf(l.getId()).stream();
                        } catch (Exception e) {
                            return java.util.stream.Stream.empty();
                        }
                    })
                    .filter(r -> r.getReportedBy() > 0 && r.getStatus() != Repayment.Verification.PENDING)
                    .toList()) {
                Label line = Ui.label(Formats.date(r.getPaidDate()) + "   " + Formats.inr(r.getAmount())
                        + (r.getNote() == null ? "" : "   ·   " + r.getNote()), "small");
                line.setWrapText(true);
                HBox.setHgrow(line, Priority.ALWAYS);
                Label status = Ui.label(r.getStatus().getLabel(), "pill", switch (r.getStatus()) {
                    case CONFIRMED -> "evidence-full";
                    case DISPUTED -> "evidence-half";
                    default -> "evidence-none";
                });
                status.setMinWidth(Region.USE_PREF_SIZE);
                HBox row = new HBox(10, line, status);
                row.setAlignment(Pos.CENTER_LEFT);
                history.getChildren().add(row);
            }
            if (history.getChildren().isEmpty()) history.getChildren().add(Ui.label("Nothing yet.", "muted"));
        } catch (Exception e) {
            Ui.error("Could not load confirmations", e);
        }
    }

    private VBox card(CommunityService.Claim c) {
        Repayment r = c.repayment();
        Loan l = c.loan();
        Label who = Ui.label(c.lenderName(), "section-title");
        Label what = Ui.label(l.getOrigin() == Loan.Origin.REPORTED
                ? "Says you borrowed " + Formats.inr(l.getPrincipal()) + " on " + Formats.date(l.getIssueDate())
                + " and repaid " + Formats.inr(r.getAmount()) + " (with interest) on " + Formats.date(r.getPaidDate())
                : "Says you paid an instalment of " + Formats.inr(r.getAmount()) + " on " + Formats.date(r.getPaidDate())
                + " for loan #" + l.getId());
        what.setWrapText(true);
        Label timing = Ui.label(r.isOnTime() ? "On time" : "Late (due " + Formats.date(r.getDueDate()) + ")",
                "pill", r.isOnTime() ? "evidence-full" : "evidence-half");
        timing.setMinWidth(Region.USE_PREF_SIZE);
        VBox text = new VBox(4, who, what, timing);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button yes = new Button("Yes, I paid this", Icons.of("check", 16));
        yes.getStyleClass().add("btn-primary");
        yes.setOnAction(e -> confirm(r));
        Button no = new Button("This is wrong", Icons.of("cross", 16));
        no.getStyleClass().add("btn-danger");
        no.setOnAction(e -> dispute(r));
        VBox buttons = new VBox(8, yes, no);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        for (Button b : new Button[]{yes, no}) b.setMaxWidth(Double.MAX_VALUE);

        HBox row = new HBox(16, new Avatar(c.lenderName(), initials(c.lenderName()), 44), text, buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(row);
        card.getStyleClass().add("claim-card");
        return card;
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String p : name.split("\\s+")) if (!p.isEmpty() && sb.length() < 2) sb.append(Character.toUpperCase(p.charAt(0)));
        return sb.toString();
    }

    private void confirm(Repayment r) {
        try {
            int before = ctx.scores().scoreValue(shell.user().getVendorId());
            ctx.community().confirm(shell.user(), r.getId());
            int after = ctx.scores().scoreValue(shell.user().getVendorId());
            shell.refreshAll();
            shell.toast("Confirmed — your score " + (after > before ? "rose from " + before + " to " + after : "is now " + after));
            if (after > before) com.trustlend.ui.component.Confetti.burst(shell);
        } catch (Exception e) {
            Ui.error("Could not confirm", e);
        }
    }

    private void dispute(Repayment r) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Dispute this report");
        d.setHeaderText("What is wrong with it? TrustLend HQ will look into it.");
        d.getEditor().setPromptText("e.g. I never borrowed from them");
        Ui.style(d.getDialogPane());
        d.showAndWait().ifPresent(reason -> {
            try {
                ctx.community().dispute(shell.user(), r.getId(), reason);
                shell.refreshAll();
                shell.toastInfo("Disputed — it no longer counts, and HQ will review it");
            } catch (Exception e) {
                Ui.error("Could not dispute", e);
            }
        });
    }
}
