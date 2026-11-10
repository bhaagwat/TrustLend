package com.trustlend.ui.component;

import com.trustlend.model.RiskBand;
import com.trustlend.service.AppContext;
import com.trustlend.service.LoanService;
import com.trustlend.service.ScoreService;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * "What if…" simulator on the vendor profile. Moving a slider re-runs the complete scoring
 * engine (including trust propagation) on a hypothetical copy of the data and shows the
 * projected score and loan offer. Nothing is saved.
 */
public class WhatIfPanel extends VBox {

    private final AppContext ctx;
    private final long vendorId;

    private final Slider sales = slider(12);
    private final Slider instalments = slider(10);
    private final Slider vouches = slider(5);
    private final CheckBox defaults = new CheckBox("Stops paying and defaults on a loan");
    private final Label salesValue = Ui.label("", "whatif-value");
    private final Label instalmentValue = Ui.label("", "whatif-value");
    private final Label vouchValue = Ui.label("", "whatif-value");

    private final IntegerProperty shownScore = new SimpleIntegerProperty(0);
    private final Label projected = Ui.label("—", "whatif-score");
    private final Label delta = Ui.label("", "whatif-delta");
    private final Label band = Ui.label("", "pill");
    private final Label now = Ui.label("", "muted", "small");
    private final Label offer = Ui.label("", "whatif-offer");
    private final VBox assumptions = new VBox(3);
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private boolean loading;

    public WhatIfPanel(AppContext ctx, long vendorId) {
        super(14);
        this.ctx = ctx;
        this.vendorId = vendorId;
        getStyleClass().addAll("card", "whatif");

        Label title = Ui.label("What if…", "section-title");
        Label sub = Ui.label("Move the sliders to see how the score and loan offer would change. "
                + "The full scoring engine runs on a hypothetical copy of the data — nothing is saved.", "muted", "small");
        sub.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        ColumnConstraints c0 = new ColumnConstraints(200);
        ColumnConstraints c1 = new ColumnConstraints(160, 260, Double.MAX_VALUE);
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(120);
        grid.getColumnConstraints().addAll(c0, c1, c2);
        grid.addRow(0, Ui.label("Keeps selling 6 days a week", "whatif-label"), sales, salesValue);
        grid.addRow(1, Ui.label("Pays instalments on time", "whatif-label"), instalments, instalmentValue);
        grid.addRow(2, Ui.label("New vouches from top vendors", "whatif-label"), vouches, vouchValue);
        defaults.getStyleClass().add("whatif-default");
        grid.add(defaults, 0, 3, 3, 1);

        Button best = new Button("Best case");
        best.getStyleClass().add("btn-ghost");
        best.setOnAction(e -> preset(12, 10, 3, false));
        Button worst = new Button("Worst case");
        worst.getStyleClass().add("btn-ghost");
        worst.setOnAction(e -> preset(0, 0, 0, true));
        Button reset = new Button("Reset");
        reset.getStyleClass().add("btn-ghost");
        reset.setOnAction(e -> preset(0, 0, 0, false));
        VBox controls = new VBox(12, grid, Ui.row(8, best, worst, reset));
        HBox.setHgrow(controls, Priority.ALWAYS);

        projected.setMinWidth(Region.USE_PREF_SIZE);
        delta.setMinWidth(Region.USE_PREF_SIZE);
        band.setMinWidth(Region.USE_PREF_SIZE);
        offer.setWrapText(true);
        HBox scoreRow = new HBox(10, projected, delta);
        scoreRow.setAlignment(Pos.BASELINE_LEFT);
        VBox result = new VBox(6, Ui.label("PROJECTED SCORE", "form-label"), scoreRow, Ui.row(8, band, now), offer, assumptions);
        result.getStyleClass().add("whatif-result");
        result.setPrefWidth(330);
        result.setMinWidth(300);

        HBox body = new HBox(24, controls, result);
        getChildren().addAll(title, sub, body);

        shownScore.addListener((o, a, b) -> projected.setText(String.valueOf(b.intValue())));
        debounce.setOnFinished(e -> recompute());
        for (Slider s : new Slider[]{sales, instalments, vouches}) {
            s.valueProperty().addListener((o, a, b) -> changed());
        }
        defaults.selectedProperty().addListener((o, a, b) -> changed());
        updateLabels();
    }

    private static Slider slider(int max) {
        Slider s = new Slider(0, max, 0);
        s.setMajorTickUnit(1);
        s.setMinorTickCount(0);
        s.setSnapToTicks(true);
        s.setBlockIncrement(1);
        s.setMaxWidth(Double.MAX_VALUE);
        return s;
    }

    private void preset(int weeks, int paid, int newVouches, boolean def) {
        sales.setValue(weeks);
        instalments.setValue(paid);
        vouches.setValue(newVouches);
        defaults.setSelected(def);
        changed();
    }

    private void changed() {
        updateLabels();
        debounce.playFromStart();
    }

    private void updateLabels() {
        int w = (int) Math.round(sales.getValue());
        int p = (int) Math.round(instalments.getValue());
        int v = (int) Math.round(vouches.getValue());
        salesValue.setText(w == 0 ? "no change" : "+" + w + " week" + (w == 1 ? "" : "s"));
        instalmentValue.setText(p == 0 ? "no change" : "+" + p + " on time");
        vouchValue.setText(v == 0 ? "no change" : "+" + v + " vouch" + (v == 1 ? "" : "es"));
        instalments.setDisable(defaults.isSelected());
    }

    /** Called by the profile view whenever the vendor's data changes. */
    public void refresh() {
        recompute();
    }

    private void recompute() {
        if (loading) {
            debounce.playFromStart();
            return;
        }
        loading = true;
        ScoreService.Scenario scenario = new ScoreService.Scenario((int) Math.round(sales.getValue()),
                (int) Math.round(instalments.getValue()), (int) Math.round(vouches.getValue()), defaults.isSelected());
        Ui.async(() -> ctx.scores().project(vendorId, scenario), this::show, err -> {
            loading = false;
            offer.setText("Could not simulate: " + err.getMessage());
        });
    }

    private void show(ScoreService.Projection p) {
        loading = false;
        int target = p.projected().score();
        if (shownScore.get() == 0) {
            shownScore.set(target);
        } else {
            new Timeline(new KeyFrame(Duration.millis(500), new KeyValue(shownScore, target))).play();
        }
        int d = p.delta();
        delta.setText(d == 0 ? "±0" : (d > 0 ? "▲ +" : "▼ ") + d);
        delta.getStyleClass().removeAll("up", "down");
        if (d != 0) delta.getStyleClass().add(d > 0 ? "up" : "down");

        RiskBand b = p.projected().band();
        band.setText(b.getLabel());
        band.getStyleClass().setAll("label", "pill", b.styleClass());
        now.setText("today " + p.current().score() + " · " + p.current().band().getLabel());

        LoanService.Terms then = ctx.loans().termsForScore(target);
        LoanService.Terms today = ctx.loans().termsForScore(p.current().score());
        boolean defaulted = p.projected().reasons().stream().anyMatch(r -> r.contains("defaulted"));
        if (defaulted) {
            offer.setText("Not eligible: a defaulted loan blocks any new credit");
        } else if (!then.eligible()) {
            offer.setText("Not eligible for a loan at this score (minimum "
                    + com.trustlend.config.AppConfig.getInstance().getInt("loan.min.score") + ")");
        } else {
            String text = "Could borrow up to " + Formats.inr(then.maxAmount()) + " at "
                    + String.format("%.1f%%", then.interestRate()) + " p.a.";
            if (today.eligible() && (then.maxAmount() != today.maxAmount() || then.interestRate() != today.interestRate())) {
                text += "  (today: " + Formats.inr(today.maxAmount()) + " at " + String.format("%.1f%%", today.interestRate()) + ")";
            } else if (!today.eligible()) {
                text += "  (today: not eligible)";
            }
            offer.setText(text);
        }
        assumptions.getChildren().clear();
        for (String a : p.assumptions()) {
            Label l = Ui.label("• " + a, "muted", "small");
            l.setWrapText(true);
            assumptions.getChildren().add(l);
        }
    }
}
