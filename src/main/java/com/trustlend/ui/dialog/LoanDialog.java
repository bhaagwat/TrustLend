package com.trustlend.ui.dialog;

import com.trustlend.exception.LoanEligibilityException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.service.LoanService;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import com.trustlend.util.Validators;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** Loan application with a live eligibility check and instalment preview. */
public class LoanDialog extends FormDialog<Loan> {

    private final AppContext ctx;
    private final ComboBox<Vendor> vendor;
    private final TextField amount = new TextField();
    private final Spinner<Integer> tenure = new Spinner<>(4, 52, 12, 2);
    private final DatePicker issue = new DatePicker(LocalDate.now());
    private final Label offerText = Ui.label("", "strong");
    private final Label offerSub = Ui.label("", "muted", "small");
    private final Label preview = Ui.label("", "small");
    private LoanService.Offer offer;

    public LoanDialog(AppContext ctx, List<Vendor> vendors, Vendor preselected) {
        super("New loan", "Issue a micro-loan", "Issue loan");
        this.ctx = ctx;
        vendor = new ComboBox<>(FXCollections.observableArrayList(vendors));
        vendor.setPromptText("Choose vendor");
        amount.setPromptText("Amount in ₹");
        amount.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().matches("[0-9,]{0,9}") ? c : null));

        VBox offerBox = new VBox(3, offerText, offerSub);
        offerBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e6e2d8; "
                + "-fx-border-radius: 10; -fx-padding: 12;");

        addRow("Vendor", "vendor", vendor);
        addFullRow(offerBox);
        addRow("Amount (₹)", "amount", amount);
        addRow("Tenure (weeks)", "tenure (weeks)", tenure);
        addRow("Issue date", "issue date", issue);
        addFullRow(preview);

        vendor.valueProperty().addListener((o, a, v) -> updateOffer());
        amount.textProperty().addListener((o, a, b) -> updatePreview());
        tenure.valueProperty().addListener((o, a, b) -> updatePreview());

        if (preselected != null) {
            vendor.setValue(preselected);
            vendor.setDisable(true);
        }
        updateOffer();
    }

    private void updateOffer() {
        Vendor v = vendor.getValue();
        if (v == null) {
            offerText.setText("Select a vendor to see their offer");
            offerSub.setText("");
            offer = null;
            return;
        }
        try {
            offer = ctx.loans().offerFor(v.getId());
            offerText.setText("Score " + offer.score() + " · " + offer.band().getLabel());
            offerText.setStyle("-fx-text-fill: " + offer.band().getColorHex() + ";");
            offerSub.setText(offer.note());
            if (offer.maxAmount() > 0 && amount.getText().isBlank()) {
                amount.setText(String.valueOf((int) offer.maxAmount() / 2));
            }
        } catch (Exception e) {
            offerSub.setText(e.getMessage());
        }
        updatePreview();
    }

    private void updatePreview() {
        if (offer == null || offer.maxAmount() <= 0) {
            preview.setText("");
            return;
        }
        try {
            double a = Validators.parseAmount("amount", amount.getText());
            Loan sample = new Loan(0, 0, a, offer.interestRate(), tenure.getValue(), LocalDate.now(),
                    LocalDate.now().plusWeeks(tenure.getValue()), LoanStatus.ACTIVE, offer.score());
            preview.setText("Weekly instalment " + Formats.inr(sample.getWeeklyInstalment())
                    + "  ·  Total payable " + Formats.inr(sample.getTotalPayable())
                    + (a > offer.maxAmount() ? "   ⚠ above limit" : ""));
        } catch (ValidationException e) {
            preview.setText("");
        }
    }

    @Override
    protected Loan submit() throws Exception {
        if (vendor.getValue() == null) throw new ValidationException("vendor", "Choose a vendor");
        double a = Validators.parseAmount("amount", amount.getText());
        try {
            return ctx.loans().issueLoan(vendor.getValue().getId(), a, tenure.getValue(), issue.getValue());
        } catch (LoanEligibilityException e) {
            if (e.getMaxEligibleAmount() > 0) {
                amount.setText(String.valueOf((int) e.getMaxEligibleAmount()));
            }
            throw new ValidationException("amount", e.getMessage());
        }
    }
}
