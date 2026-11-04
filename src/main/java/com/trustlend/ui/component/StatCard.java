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
        value.setText(valueText);
        hint.setText(hintText);
    }
}
