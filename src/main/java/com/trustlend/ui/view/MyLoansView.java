package com.trustlend.ui.view;

import com.trustlend.model.Loan;
import com.trustlend.model.Repayment;
import com.trustlend.service.AppContext;
import com.trustlend.service.LoanService;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** Borrower: my loans from every lender, what I have paid, and what is due next. */
public class MyLoansView extends BaseView {

    private final TableView<Loan> loans = new TableView<>();
    private final TableView<Repayment> payments = new TableView<>();
    private final TableView<LoanService.Instalment> schedule = new TableView<>();
    private final Label next = Ui.label("", "section-title");

    public MyLoansView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        setHeader("My loans", "Loans from TrustLend and from lenders who reported them, with every payment");

        col(loans, "LOAN", l -> "#" + l.getId());
        col(loans, "LENDER", l -> l.getLenderName() + (l.getOrigin() == Loan.Origin.REPORTED ? " (reported)" : ""));
        col(loans, "AMOUNT", l -> Formats.inr(l.getPrincipal()));
        col(loans, "RATE", l -> String.format("%.1f%%", l.getInterestRate()));
        col(loans, "ISSUED", l -> Formats.date(l.getIssueDate()));
        col(loans, "DUE", l -> Formats.date(l.getDueDate()));
        col(loans, "PAID", l -> Formats.inr(l.getAmountRepaid()) + " of " + Formats.inr(l.getTotalPayable()));
        col(loans, "STATUS", l -> l.getStatus().getLabel());
        loans.setPlaceholder(new Label("No loans yet"));
        loans.setPrefHeight(230);

        col(payments, "PAID ON", r -> Formats.date(r.getPaidDate()));
        col(payments, "AMOUNT", r -> Formats.inr(r.getAmount()));
        col(payments, "DUE", r -> Formats.date(r.getDueDate()));
        col(payments, "ON TIME?", r -> r.isOnTime() ? "On time" : "Late");
        col(payments, "VERIFIED", r -> r.getStatus().getLabel());
        payments.setPlaceholder(new Label("Select a loan"));

        col(schedule, "WEEK", i -> String.valueOf(i.number()));
        col(schedule, "DUE", i -> Formats.date(i.dueDate()));
        col(schedule, "INSTALMENT", i -> Formats.inr(i.amount()));
        col(schedule, "BALANCE AFTER", i -> Formats.inr(i.balanceAfter()));
        schedule.setPlaceholder(new Label("Select a loan"));

        loans.getSelectionModel().selectedItemProperty().addListener((o, a, l) -> showLoan(l));
        VBox paidCard = Ui.titledCard("Payments", payments);
        VBox planCard = Ui.titledCard("Repayment plan", schedule);
        HBox.setHgrow(paidCard, Priority.ALWAYS);
        HBox.setHgrow(planCard, Priority.ALWAYS);
        VBox body = new VBox(16, next, Ui.titledCard("Loans", loans), new HBox(16, paidCard, planCard));
        setCenter(body);
    }

    private static <T> void col(TableView<T> table, String title, java.util.function.Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        table.getColumns().add(c);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void showLoan(Loan l) {
        if (l == null) return;
        try {
            payments.getItems().setAll(ctx.loans().repaymentsOf(l.getId()));
            schedule.getItems().setAll(LoanService.schedule(l.getPrincipal(), l.getInterestRate(), l.getTenureWeeks(), l.getIssueDate()));
        } catch (Exception e) {
            Ui.error("Could not load the loan", e);
        }
    }

    @Override
    public void refresh() {
        try {
            List<Loan> mine = ctx.loans().loansOf(shell.user().getVendorId());
            loans.getItems().setAll(mine);
            Loan open = mine.stream().filter(l -> l.getStatus().isOpen()).findFirst().orElse(null);
            if (open == null) {
                next.setText("No loan running right now");
            } else {
                int paidCount = ctx.loans().repaymentsOf(open.getId()).size();
                LocalDate due = open.instalmentDueDate(Math.min(paidCount + 1, open.getTenureWeeks()));
                next.setText("Next instalment: " + Formats.inr(open.getWeeklyInstalment()) + " to " + open.getLenderName()
                        + " by " + Formats.date(due) + "   ·   " + Formats.inr(open.getOutstanding()) + " left");
            }
            if (!mine.isEmpty()) loans.getSelectionModel().select(open != null ? open : mine.get(0));
        } catch (Exception e) {
            Ui.error("Could not load your loans", e);
        }
    }
}
