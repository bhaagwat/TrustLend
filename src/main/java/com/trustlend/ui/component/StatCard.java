package com.trustlend.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Dashboard KPI tile: label, big value, hint line and a coloured accent bar. */
public class StatCard extends VBox {

    private final Label value = new Label();
    private final Label hint = new Label();

    public StatCard(String title, String accentColor) {
        getStyleClass().addAll("card", "stat-card");
        Region accent = new Region();
        accent.getStyleClass().add("stat-accent");
        accent.setStyle("-fx-background-color: " + accentColor + ";");
        accent.setMaxWidth(36);

        Label label = new Label(title);
        label.getStyleClass().add("stat-label");
        value.getStyleClass().add("stat-value");
        hint.getStyleClass().add("stat-hint");
        hint.setWrapText(true);

        getChildren().addAll(accent, label, value, hint);
        HBox.setHgrow(this, Priority.ALWAYS);
        setMaxWidth(Double.MAX_VALUE);
    }

    public void update(String valueText, String hintText) {
        if (counter != null) counter.stop();
        value.setText(valueText);
        hint.setText(hintText);
    }

    private final javafx.beans.property.DoubleProperty shown = new javafx.beans.property.SimpleDoubleProperty(0);
    private javafx.animation.Timeline counter;
    private java.util.function.DoubleFunction<String> format = v -> String.valueOf(Math.round(v));

    /** Counts the number up (or down) from its current value to {@code target} in ~0.9 s. */
    public void animateTo(double target, java.util.function.DoubleFunction<String> fmt, String hintText) {
        format = fmt;
        hint.setText(hintText);
        if (counter != null) counter.stop();
        if (shown.get() == target) {
            value.setText(fmt.apply(target));
            return;
        }
        counter = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(900),
                new javafx.animation.KeyValue(shown, target, javafx.animation.Interpolator.EASE_OUT)));
        counter.play();
    }

    {
        shown.addListener((o, a, b) -> value.setText(format.apply(b.doubleValue())));
    }
}
