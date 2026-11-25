package com.trustlend.ui.dialog;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.Loan;
import com.trustlend.model.User;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * A lender tells TrustLend about a loan given outside the app that was repaid — for example a
 * moneylender, a self-help group, or a wholesaler who gave stock on credit. The repayment is
 * stored as "awaiting the borrower", and counts fully once the borrower confirms it.
 */
public class ReportLoanDialog extends FormDialog<Loan> {

    private final AppContext ctx;
    private final User lender;
    private final ComboBox<Vendor> borrower;
    private final TextField amount = new TextField("5000");
    private final TextField interest = new TextField("24");
    private final Spinner<Integer> weeks = new Spinner<>(1, 104, 10);
    private final DatePicker issued = new DatePicker(LocalDate.now().minusWeeks(14));
    private final DatePicker repaid = new DatePicker(LocalDate.now().minusWeeks(4));

    public ReportLoanDialog(AppContext ctx, User lender, Vendor preselected) throws Exception {
        super("Report a past loan", "A loan you gave outside TrustLend that was paid back", "Report loan");
        this.ctx = ctx;
        this.lender = lender;
        List<Vendor> all = ctx.vendors().findAll().stream()
                .sorted(Comparator.comparing(Vendor::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        borrower = new ComboBox<>(FXCollections.observableArrayList(all));
        borrower.setConverter(new StringConverter<>() {
            @Override public String toString(Vendor v) {
                return v == null ? "" : v.getName() + "  ·  " + CommunityService.trustId(v.getId()) + "  ·  " + v.getMarketArea();
            }
            @Override public Vendor fromString(String s) { return null; }
        });
        borrower.setPromptText("Choose the borrower");
        borrower.setMaxWidth(Double.MAX_VALUE);
        if (preselected != null) borrower.setValue(preselected);
        for (TextField f : new TextField[]{amount, interest}) {
            f.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                    c.getControlNewText().matches("[0-9,.]{0,9}") ? c : null));
        }
        weeks.setEditable(true);

        Label how = Ui.label("Example: \"I lent Ramesh ₹5,000 in March at 24% a year and he paid it all back on time.\" "
                + "The borrower is asked to confirm it; until then it counts half towards their score. "
                + "Reports that borrowers dispute lower your credibility as a lender.", "muted", "small");
        how.setWrapText(true);
        how.setMaxWidth(440);
        addFullRow(how);
        addRow("Borrower", "borrower", borrower);
        addRow("Amount lent (₹)", "amount", amount);
        addRow("Interest (% a year)", "interest", interest);
        addRow("Tenure (weeks)", "tenure (weeks)", weeks);
        addRow("Given on", "issue date", issued);
        addRow("Fully repaid on", "repaid on", repaid);
    }

    @Override
    protected Loan submit() throws Exception {
        if (borrower.getValue() == null) throw new ValidationException("borrower", "Choose the borrower");
        double a = com.trustlend.util.Validators.parseAmount("amount", amount.getText());
        double rate;
        try {
            rate = Double.parseDouble(interest.getText().replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("interest", "Enter the yearly interest, e.g. 24");
        }
        Loan loan = ctx.community().reportPastLoan(lender, borrower.getValue().getId(), a, rate, weeks.getValue(),
                issued.getValue(), repaid.getValue());
        loan.setVendorName(borrower.getValue().getName());
        return loan;
    }
}
