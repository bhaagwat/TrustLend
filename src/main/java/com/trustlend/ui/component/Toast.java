package com.trustlend.ui.component;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Non-blocking notification that slides in at the bottom-right and fades away. */
public final class Toast {

    public enum Kind { SUCCESS, ERROR, INFO }

    private Toast() { }

    public static void show(StackPane layer, String message, Kind kind) {
        String glyph = switch (kind) {
            case SUCCESS -> "✔";
            case ERROR -> "✖";
            case INFO -> "ℹ";
        };
        Label icon = new Label(glyph);
        icon.setStyle("-fx-font-size: 14px;");
        Label text = new Label(message);
        text.setWrapText(true);
        text.setMaxWidth(380);
        HBox box = new HBox(icon, text);
        box.getStyleClass().addAll("toast", "toast-" + kind.name().toLowerCase());
        box.setMaxSize(HBox.USE_PREF_SIZE, HBox.USE_PREF_SIZE);
        box.setMouseTransparent(true);
        StackPane.setAlignment(box, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(box, new Insets(0, 28, 28, 0));
        layer.getChildren().add(box);

        box.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), box);
        fadeIn.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), box);
        slide.setFromY(20);
        slide.setToY(0);
        PauseTransition hold = new PauseTransition(Duration.seconds(kind == Kind.ERROR ? 4.5 : 3));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), box);
        fadeOut.setToValue(0);

        SequentialTransition seq = new SequentialTransition(new ParallelTransition(fadeIn, slide), hold, fadeOut);
        seq.setOnFinished(e -> layer.getChildren().remove(box));
        seq.play();
    }
}
