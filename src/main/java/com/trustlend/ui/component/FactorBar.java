package com.trustlend.ui.component;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One row of the score breakdown: name, weight, percentage and a coloured progress bar. */
public class FactorBar extends VBox {

    private final ProgressBar bar = new ProgressBar(0);
    private final Label pct = new Label();

    public FactorBar(String name, String key, double weight, String description) {
        super(6);
        Label title = new Label(name);
        title.getStyleClass().add("factor-name");
        Label w = new Label("weight " + Math.round(weight * 100) + "%");
        w.getStyleClass().addAll("muted", "small");
        pct.getStyleClass().add("strong");
        HBox top = new HBox(8, title, w);
        HBox spacerRow = new HBox(top);
        HBox.setHgrow(top, Priority.ALWAYS);
        spacerRow.getChildren().add(pct);

        bar.getStyleClass().addAll("factor-bar", "factor-" + key);
        bar.setMaxWidth(Double.MAX_VALUE);
        Tooltip.install(this, new Tooltip(description));
        getChildren().addAll(spacerRow, bar);
    }

    public void setValue(double value) {
        bar.setProgress(value);
        pct.setText(Math.round(value * 100) + "%");
    }
}
