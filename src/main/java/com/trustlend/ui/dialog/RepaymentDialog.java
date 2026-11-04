package com.trustlend.ui.dialog;

import com.trustlend.model.Loan;
import com.trustlend.model.Repayment;
import com.trustlend.service.AppContext;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import com.trustlend.util.Validators;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.time.LocalDate;

/** Record an instalment against a loan. */
public class RepaymentDialog extends FormDialog<Repayment> {

    private final AppContext ctx;
    private final Loan loan;
    private final TextField amount = new TextField();
    private final DatePicker paid = new DatePicker(LocalDate.now());

    public RepaymentDialog(AppContext ctx, Loan loan) {
        super("Record repayment", "Loan #" + loan.getId() + " · " + loan.getVendorName(), "Record payment");
        this.ctx = ctx;
        this.loan = loan;
        amount.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().matches("[0-9,.]{0,10}") ? c : null));
        amount.setText(String.valueOf((long) Math.min(loan.getWeeklyInstalment(), Math.ceil(loan.getOutstanding()))));

        Label summary = Ui.label("Outstanding " + Formats.inr(loan.getOutstanding()) + " of "
                + Formats.inr(loan.getTotalPayable()) + "  ·  weekly instalment " + Formats.inr(loan.getWeeklyInstalment())
                + "  ·  due " + Formats.date(loan.getDueDate()), "muted", "small");
        summary.setWrapText(true);
        addFullRow(summary);
        addRow("Amount (₹)", "amount", amount);
        addRow("Paid on", "payment date", paid);
    }

    @Override
    protected Repayment submit() throws Exception {
        double a = Validators.parseAmount("amount", amount.getText());
        return ctx.loans().recordRepayment(loan.getId(), a, paid.getValue());
    }
}
