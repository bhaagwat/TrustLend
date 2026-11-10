package com.trustlend.ui.dialog;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.Loan;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.service.LoanService;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Icons;
import com.trustlend.ui.component.ScoreGauge;
import com.trustlend.util.Formats;
import com.trustlend.util.Validators;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.List;

/**
 * Loan application in two steps, like a real lending app:
 * <ol>
 *   <li>the application form (vendor, amount, tenure) with the vendor's current offer;</li>
 *   <li>a <b>decision card</b> — APPROVED / APPROVED FOR A LOWER AMOUNT / DECLINED — with the
 *       reasons, the terms and the full week-by-week repayment schedule.</li>
 * </ol>
 * Only "Disburse" on the decision card creates the loan.
 */
public class LoanDialog extends Dialog<Loan> {

    private final AppContext ctx;
    private final ComboBox<Vendor> vendor;
    private final TextField amount = new TextField();
    private final Spinner<Integer> tenure = new Spinner<>(4, 52, 12, 2);
    private final DatePicker issue = new DatePicker(LocalDate.now());
    private final Label offerText = Ui.label("", "strong");
    private final Label offerSub = Ui.label("", "muted", "small");
    private final Label error = Ui.label("", "error-text");
    private final StackPane pages = new StackPane();
    private final VBox formPage;
    private LoanService.Decision decision;

    public LoanDialog(AppContext ctx, List<Vendor> vendors, Vendor preselected) {
        this.ctx = ctx;
        setTitle("Loan application");
        setHeaderText(null);
        Ui.style(getDialogPane());
        getDialogPane().getStyleClass().add("loan-dialog");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        vendor = new ComboBox<>(FXCollections.observableArrayList(vendors));
        vendor.setPromptText("Choose vendor");
        amount.setPromptText("Amount in ₹");
        amount.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().matches("[0-9,]{0,9}") ? c : null));
        for (javafx.scene.control.Control c : List.of(vendor, amount, tenure, issue)) c.setMaxWidth(Double.MAX_VALUE);

        VBox offerBox = new VBox(3, offerText, offerSub);
        offerBox.getStyleClass().add("offer-box");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints(130);
        ColumnConstraints inputs = new ColumnConstraints(280, 340, Double.MAX_VALUE);
        inputs.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, inputs);
        grid.addRow(0, Ui.label("Vendor", "form-label"), vendor);
        grid.add(offerBox, 0, 1, 2, 1);
        grid.addRow(2, Ui.label("Amount (₹)", "form-label"), amount);
        grid.addRow(3, Ui.label("Tenure (weeks)", "form-label"), tenure);
        grid.addRow(4, Ui.label("Issue date", "form-label"), issue);

        Button check = new Button("Check eligibility  →");
        check.getStyleClass().add("btn-accent");
        check.setDefaultButton(true);
        check.setOnAction(e -> evaluate());
        error.setWrapText(true);
        Label hint = Ui.label("The decision is instant and explained. Nothing is saved until you disburse.", "muted", "small");
        hint.setWrapText(true);
        formPage = new VBox(14, Ui.label("Apply for a micro-loan", "dialog-title"), grid, error,
                new HBox(10, hint, Ui.hSpacer(), check));
        formPage.setPrefWidth(560);

        pages.getChildren().add(formPage);
        getDialogPane().setContent(pages);

        vendor.valueProperty().addListener((o, a, v) -> updateOffer());
        if (preselected != null) {
            vendor.setValue(preselected);
            vendor.setDisable(true);
        }
        updateOffer();
        setResultConverter(b -> null);
    }

    private void updateOffer() {
        Vendor v = vendor.getValue();
        if (v == null) {
            offerText.setText("Select a vendor to see their offer");
            offerText.setStyle(null);
            offerSub.setText("");
            return;
        }
        try {
            LoanService.Offer offer = ctx.loans().offerFor(v.getId());
            offerText.setText("Score " + offer.score() + " · " + offer.band().getLabel());
            offerText.setStyle("-fx-text-fill: " + offer.band().getColorHex() + ";");
            offerSub.setText(offer.note());
            if (offer.maxAmount() > 0 && amount.getText().isBlank()) {
                amount.setText(String.valueOf((int) offer.maxAmount() / 2));
            } else if (amount.getText().isBlank()) {
                amount.setText("10000");
            }
        } catch (Exception e) {
            offerSub.setText(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ step 2: the decision

    private void evaluate() {
        error.setText("");
        try {
            if (vendor.getValue() == null) throw new ValidationException("vendor", "Choose a vendor");
            double a = Validators.parseAmount("amount", amount.getText());
            decision = ctx.loans().decide(vendor.getValue().getId(), a, tenure.getValue(), issue.getValue());
            showDecision();
        } catch (Exception e) {
            error.setText("⚠  " + e.getMessage());
        }
    }

    private void showDecision() {
        LoanService.Decision d = decision;
        String tone = switch (d.verdict()) {
            case APPROVED -> "approved";
            case REDUCED -> "reduced";
            case DECLINED -> "declined";
        };

        // banner with the animated stamp
        Label stamp = Ui.label(switch (d.verdict()) {
            case APPROVED -> "APPROVED";
            case REDUCED -> "APPROVED · LOWER AMOUNT";
            case DECLINED -> "DECLINED";
        }, "decision-stamp");
        stamp.setMinWidth(Region.USE_PREF_SIZE);
        Label headline = Ui.label(d.headline(), "decision-headline");
        headline.setWrapText(true);
        Label who = Ui.label(d.vendor().getName() + "  ·  " + d.vendor().describeRole(), "decision-sub");
        VBox bannerText = new VBox(6, stamp, headline, who);
        HBox.setHgrow(bannerText, Priority.ALWAYS);
        ScoreGauge gauge = new ScoreGauge(150, 300, 900);
        gauge.getStyleClass().add("decision-gauge");
        HBox banner = new HBox(18, Icons.of(d.verdict() == LoanService.Verdict.DECLINED ? "cross" : "check", 44), bannerText, gauge);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().addAll("decision-banner", tone);

        // why
        VBox why = new VBox(6, Ui.label("WHY", "form-label"));
        for (String r : d.reasons()) {
            Label l = Ui.label("•  " + r, "small");
            l.setWrapText(true);
            why.getChildren().add(l);
        }

        VBox content = new VBox(14, banner);
        if (d.approvedAmount() > 0) {
            GridPane terms = new GridPane();
            terms.setHgap(10);
            terms.setVgap(10);
            String[][] cells = {
                    {"Amount", Formats.inr(d.approvedAmount())},
                    {"Interest", String.format("%.1f%% p.a.", d.interestRate())},
                    {"Tenure", d.tenureWeeks() + " weeks"},
                    {"Weekly instalment", Formats.inr(d.weeklyInstalment())},
                    {"Total interest", Formats.inr(d.totalInterest())},
                    {"Total payable", Formats.inr(d.totalPayable())}};
            for (int i = 0; i < cells.length; i++) {
                VBox tile = new VBox(2, Ui.label(cells[i][0], "term-label"), Ui.label(cells[i][1], "term-value"));
                tile.getStyleClass().add("term-tile");
                tile.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(tile, Priority.ALWAYS);
                terms.add(tile, i % 3, i / 3);
            }
            content.getChildren().addAll(terms, why, Ui.label("REPAYMENT SCHEDULE", "form-label"), scheduleTable(d));
        } else {
            Label tip = Ui.label("Tip: open this vendor's profile and use the \"What if…\" simulator to see what "
                    + "would make them eligible — e.g. 8 more weeks of regular sales or two new vouches.", "muted", "small");
            tip.setWrapText(true);
            content.getChildren().addAll(why, tip);
        }

        Button change = new Button("←  Change application");
        change.getStyleClass().add("btn-ghost");
        change.setOnAction(e -> {
            pages.getChildren().setAll(formPage);
            getDialogPane().getScene().getWindow().sizeToScene();
        });
        HBox buttons = new HBox(10, change, Ui.hSpacer());
        if (d.approvedAmount() > 0) {
            Button disburse = new Button("Disburse " + Formats.inr(d.approvedAmount()));
            disburse.getStyleClass().add("btn-accent");
            disburse.setDefaultButton(true);
            disburse.setOnAction(e -> disburse());
            buttons.getChildren().add(disburse);
        }
        content.getChildren().add(buttons);
        content.setPrefWidth(640);
        pages.getChildren().setAll(content);
        getDialogPane().getScene().getWindow().sizeToScene();

        gauge.setScore(d.score());
        stamp.setScaleX(2.4);
        stamp.setScaleY(2.4);
        stamp.setOpacity(0);
        ScaleTransition s = new ScaleTransition(Duration.millis(380), stamp);
        s.setToX(1);
        s.setToY(1);
        FadeTransition f = new FadeTransition(Duration.millis(300), stamp);
        f.setToValue(1);
        new ParallelTransition(s, f).play();
    }

    private TableView<LoanService.Instalment> scheduleTable(LoanService.Decision d) {
        TableView<LoanService.Instalment> t = new TableView<>(FXCollections.observableArrayList(d.schedule()));
        TableColumn<LoanService.Instalment, String> n = new TableColumn<>("WEEK");
        n.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(String.valueOf(c.getValue().number())));
        TableColumn<LoanService.Instalment, String> due = new TableColumn<>("DUE DATE");
        due.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Formats.date(c.getValue().dueDate())));
        TableColumn<LoanService.Instalment, String> pay = new TableColumn<>("INSTALMENT");
        pay.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Formats.inr(c.getValue().amount())));
        TableColumn<LoanService.Instalment, String> bal = new TableColumn<>("BALANCE AFTER");
        bal.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Formats.inr(c.getValue().balanceAfter())));
        t.getColumns().addAll(List.of(n, due, pay, bal));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPrefHeight(200);
        return t;
    }

    private void disburse() {
        try {
            Loan loan = ctx.loans().issueLoan(decision.vendor().getId(), decision.approvedAmount(),
                    decision.tenureWeeks(), decision.issueDate());
            setResult(loan);
            close();
        } catch (Exception e) {
            Ui.error("Could not disburse", e);
        }
    }
}
