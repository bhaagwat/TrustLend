package com.trustlend.ui.component;

import com.trustlend.model.RiskBand;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * A 240° arc gauge for the 300-900 score, built from scene-graph shapes (no images).
 * The value animates from 300 to the score with a {@link Timeline}.
 */
public class ScoreGauge extends Pane {

    private static final double START_ANGLE = 210;   // degrees, measured counter-clockwise from 3 o'clock
    private static final double SWEEP = 240;

    private final Arc track = new Arc();
    private final Arc value = new Arc();
    private final Text scoreText = new Text("—");
    private final Text caption = new Text("TRUST SCORE");
    private final Text bandText = new Text("");
    private final DoubleProperty animated = new SimpleDoubleProperty(300);
    private final int min;
    private final int max;

    public ScoreGauge(double size, int min, int max) {
        this.min = min;
        this.max = max;
        setPrefSize(size, size * 0.86);
        setMinSize(size, size * 0.86);
        setMaxSize(size, size * 0.86);

        double r = size / 2 - 14;
        for (Arc arc : new Arc[]{track, value}) {
            arc.setCenterX(size / 2);
            arc.setCenterY(size / 2);
            arc.setRadiusX(r);
            arc.setRadiusY(r);
            arc.setStartAngle(START_ANGLE);
            arc.setType(ArcType.OPEN);
            arc.setFill(null);
            arc.setStrokeWidth(16);
            arc.setStrokeLineCap(StrokeLineCap.ROUND);
        }
        track.setLength(-SWEEP);
        track.setStroke(Color.web("#eeebe3"));
        value.setLength(0);

        // Fonts are set in code (not CSS) so text widths are correct before the first CSS pass
        scoreText.setFont(Font.font(null, FontWeight.BOLD, size * 0.2));
        scoreText.setFill(Color.web("#1f2333"));
        caption.setFont(Font.font(null, FontWeight.BOLD, 11));
        caption.setFill(Color.web("#6b6f80"));
        bandText.setFont(Font.font(null, FontWeight.BOLD, 14));
        // colours above are defaults; CSS can override them (dark mode, story mode, certificate)
        getStyleClass().add("score-gauge");
        track.getStyleClass().add("gauge-track");
        scoreText.getStyleClass().add("gauge-score");
        caption.getStyleClass().add("gauge-caption");
        getChildren().addAll(track, value, scoreText, caption, bandText);

        animated.addListener((obs, old, now) -> render(now.intValue(), size));
        render(min, size);
    }

    /** Animates the gauge to a new score. */
    public void setScore(int score) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(animated, animated.get())),
                new KeyFrame(Duration.millis(900), new KeyValue(animated, score)));
        tl.play();
    }

    private void render(int score, double size) {
        double fraction = Math.max(0, Math.min(1, (score - min) / (double) (max - min)));
        value.setLength(-SWEEP * fraction);
        RiskBand band = RiskBand.fromScore(score);
        Color color = Color.web(band.getColorHex());
        value.setStroke(color);

        scoreText.setText(String.valueOf(score));
        centre(scoreText, size / 2 + size * 0.07);
        centre(caption, size / 2 - size * 0.15);
        bandText.setText(band.getLabel().toUpperCase());
        bandText.setFill(color);
        centre(bandText, size / 2 + size * 0.2);
    }

    private void centre(Text t, double y) {
        double w = t.getLayoutBounds().getWidth();
        t.setLayoutX(getPrefWidth() / 2 - w / 2);
        t.setLayoutY(y);
    }
}
