package com.trustlend.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** Round initials avatar whose colour is derived from the name (same person, same colour). */
public class Avatar extends StackPane {

    private static final String[] PALETTE = {
            "#0f766e", "#1a56db", "#8b5cf6", "#c2410c", "#be185d", "#0e7490", "#4d7c0f", "#b45309"
    };

    public Avatar(String name, String initials, double size) {
        Circle circle = new Circle(size / 2);
        int idx = Math.floorMod(name.hashCode(), PALETTE.length);
        circle.setFill(Color.web(PALETTE[idx]));
        Label l = new Label(initials);
        l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + (size * 0.38) + "px;");
        getChildren().addAll(circle, l);
        setMinSize(size, size);
        setMaxSize(size, size);
    }

    public static String colorFor(String name) {
        return PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)];
    }
}
