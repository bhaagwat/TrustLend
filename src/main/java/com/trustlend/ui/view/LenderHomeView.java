package com.trustlend.ui.view;

import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Icons;
import com.trustlend.ui.component.StatCard;
import com.trustlend.ui.dialog.LoanDialog;
import com.trustlend.ui.dialog.ReportLoanDialog;
import com.trustlend.util.Formats;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A lender's home: their own lending, how much the community trusts their reports, and the
 * payments they reported that borrowers have not confirmed yet.
 */
public class LenderHomeView extends BaseView {

    private final StatCard lent = new StatCard("Lent so far", "#0f766e");
    private final StatCard outstanding = new StatCard("Outstanding", "#1a56db");
    private final StatCard borrowers = new StatCard("Borrowers", "#f29f05");
    private final StatCard credibility = new StatCard("Your credibility", "#8b5cf6");
    private final TableView<CommunityService.Claim> waiting = new TableView<>();

    public LenderHomeView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        Button give = new Button("₹  Give a loan");
        give.getStyleClass().add("btn-accent");
        give.setOnAction(e -> giveLoan());
        Button report = new Button("Report a past loan");
        report.getStyleClass().add("btn-ghost");
        report.setOnAction(e -> reportLoan());
        Button find = new Button("Find a borrower", Icons.of("vendors", 16));
        find.getStyleClass().add("btn-ghost");
        find.setOnAction(e -> shell.navigate(MainWindow.Page.VENDORS));
        setHeader(shell.user().getDisplayName(), shell.user().getLenderType() + " · lender desk", find, report, give);

        HBox kpis = new HBox(16, lent, outstanding, borrowers, credibility);
        for (StatCard c : List.of(lent, outstanding, borrowers, credibility)) HBox.setHgrow(c, Priority.ALWAYS);

        TableColumn<CommunityService.Claim, String> who = new TableColumn<>("BORROWER");
        who.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().loan().getVendorName()));
        TableColumn<CommunityService.Claim, String> amount = new TableColumn<>("PAYMENT");
        amount.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Formats.inr(c.getValue().repayment().getAmount())));
        TableColumn<CommunityService.Claim, String> date = new TableColumn<>("PAID ON");
        date.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Formats.date(c.getValue().repayment().getPaidDate())));
        TableColumn<CommunityService.Claim, String> loan = new TableColumn<>("LOAN");
        loan.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>("#" + c.getValue().loan().getId()
                + (c.getValue().loan().getOrigin() == com.trustlend.model.Loan.Origin.REPORTED ? " (outside loan)" : "")));
        waiting.getColumns().addAll(List.of(who, amount, date, loan));
        waiting.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        waiting.setPlaceholder(new Label("Nothing waiting — every payment you reported has been confirmed"));
        waiting.setPrefHeight(240);

        Label how = Ui.label("1. Give loans through TrustLend, or report loans you gave before (\"Report a past loan\").\n"
                + "2. Record each payment you receive in \"My loans\". The borrower is asked to confirm it.\n"
                + "3. Confirmed payments raise the borrower's score — and your credibility. Payments a borrower "
                + "disputes go to TrustLend HQ; false reports lower your credibility for everyone to see.", "small");
        how.setWrapText(true);

        VBox content = new VBox(16, kpis,
                Ui.titledCard("Payments you reported, waiting for the borrower to confirm", waiting),
                Ui.titledCard("How lending on TrustLend works", how));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private void giveLoan() {
        try {
            new LoanDialog(ctx, ctx.vendors().findAll().stream().filter(com.trustlend.model.Vendor::isActive).toList(), null)
                    .asLender(shell.user()).showAndWait()
                    .ifPresent(l -> {
                        shell.refreshAll();
                        shell.toast("Loan #" + l.getId() + " of " + Formats.inr(l.getPrincipal()) + " disbursed");
                    });
        } catch (Exception e) {
            Ui.error("Could not open the loan form", e);
        }
    }

    private void reportLoan() {
        try {
            new ReportLoanDialog(ctx, shell.user(), null).showAndWait().ifPresent(l -> {
                shell.refreshAll();
                shell.toast("Reported — " + l.getVendorName() + " will be asked to confirm it");
            });
        } catch (Exception e) {
            Ui.error("Could not open the form", e);
        }
    }

    @Override
    public void refresh() {
        try {
            CommunityService.LenderStats s = ctx.community().lenderStats(shell.user());
            lent.animateTo(s.lent(), Formats::inrCompact, s.loans() + " loan(s) given or reported");
            outstanding.animateTo(s.outstanding(), Formats::inrCompact, "still to be repaid");
            borrowers.animateTo(s.borrowers(), v -> String.valueOf(Math.round(v)), "people you have lent to");
            credibility.animateTo(s.credibilityPercent(), v -> Math.round(v) + "%", s.confirmed() + " confirmed · "
                    + s.pending() + " waiting · " + s.disputed() + " disputed");
            waiting.getItems().setAll(ctx.community().awaitingConfirmation(shell.user()));
        } catch (Exception e) {
            Ui.error("Could not load your lender desk", e);
        }
    }
}
