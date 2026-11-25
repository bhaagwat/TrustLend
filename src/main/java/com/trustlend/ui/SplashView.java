package com.trustlend.ui;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Random;

/**
 * Opening screen: a slowly drifting "constellation" of trust nodes (points joined by lines when
 * they come close, like a vouch network) behind the animated TrustLend logo. Click to skip.
 */
public class SplashView extends StackPane {

    private static final int POINTS = 70;

    private final Canvas canvas = new Canvas();
    private final double[][] points = new double[POINTS][4];   // x, y, vx, vy in 0..1 units
    private final AnimationTimer timer;
    private final StackPane mark = new StackPane(Ui.label("T", "splash-mark-text"));
    private final Label title = Ui.label("TrustLend", "splash-title");
    private final Label tagline = Ui.label("Credit built on community trust", "splash-tagline");
    private final Label status = Ui.label("", "splash-status");
    private final ProgressBar progress = new ProgressBar(0);
    private Runnable onDone;
    private boolean finished;

    public SplashView() {
        getStyleClass().add("splash");
        Random r = new Random(11);
        for (double[] p : points) {
            p[0] = r.nextDouble();
            p[1] = r.nextDouble();
            p[2] = (r.nextDouble() - 0.5) * 0.03;
            p[3] = (r.nextDouble() - 0.5) * 0.03;
        }
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.setManaged(false);

        mark.getStyleClass().add("splash-mark");
        progress.getStyleClass().add("splash-progress");
        progress.setPrefWidth(280);
        VBox centre = new VBox(14, mark, title, tagline, progress, status);
        centre.setAlignment(Pos.CENTER);
        centre.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        Label skip = Ui.label("Click anywhere to skip", "splash-status");
        StackPane.setAlignment(skip, Pos.BOTTOM_CENTER);
        skip.setTranslateY(-24);
        getChildren().addAll(canvas, centre, skip);
        setOnMouseClicked(e -> finish());

        timer = new AnimationTimer() {
            private long last = -1;

            @Override
            public void handle(long now) {
                double dt = last < 0 ? 0 : Math.min(0.05, (now - last) / 1e9);
                last = now;
                draw(dt);
            }
        };
    }

    /** Plays the intro (about 2.8 s) and then calls {@code done}. */
    public void play(Runnable done) {
        this.onDone = done;
        timer.start();

        mark.setScaleX(0);
        mark.setScaleY(0);
        ScaleTransition pop = new ScaleTransition(Duration.millis(650), mark);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(new Interpolator() {               // "ease out back": overshoots, then settles
            @Override
            protected double curve(double t) {
                double u = t - 1;
                return 1 + 2.70158 * u * u * u + 1.70158 * u * u;
            }
        });
        title.setOpacity(0);
        title.setTranslateY(18);
        tagline.setOpacity(0);
        SequentialTransition intro = new SequentialTransition(pop, new ParallelTransition(
                fadeUp(title, 450), fadeUp(tagline, 650)));
        intro.play();

        String[] steps = {"Opening the ledger…", "Mapping the trust network…", "Ready"};
        Timeline load = new Timeline(new KeyFrame(Duration.millis(2600),
                new KeyValue(progress.progressProperty(), 1, Interpolator.EASE_BOTH)));
        for (int i = 0; i < steps.length; i++) {
            String text = steps[i];
            load.getKeyFrames().add(new KeyFrame(Duration.millis(300 + i * 1000), e -> status.setText(text)));
        }
        load.setOnFinished(e -> {
            PauseTransition hold = new PauseTransition(Duration.millis(250));
            hold.setOnFinished(x -> finish());
            hold.play();
        });
        load.play();
    }

    private static ParallelTransition fadeUp(javafx.scene.Node node, int millis) {
        FadeTransition f = new FadeTransition(Duration.millis(millis), node);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(millis), node);
        t.setToY(0);
        return new ParallelTransition(f, t);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        FadeTransition out = new FadeTransition(Duration.millis(350), this);
        out.setToValue(0);
        out.setOnFinished(e -> {
            timer.stop();
            if (onDone != null) onDone.run();
        });
        out.play();
    }

    private void draw(double dt) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        for (double[] p : points) {
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            if (p[0] < 0 || p[0] > 1) p[2] = -p[2];
            if (p[1] < 0 || p[1] > 1) p[3] = -p[3];
        }
        double reach = Math.min(w, h) * 0.18;
        for (int i = 0; i < POINTS; i++) {
            for (int j = i + 1; j < POINTS; j++) {
                double dx = (points[i][0] - points[j][0]) * w;
                double dy = (points[i][1] - points[j][1]) * h;
                double d = Math.hypot(dx, dy);
                if (d < reach) {
                    g.setStroke(Color.rgb(247, 183, 51, 0.35 * (1 - d / reach)));
                    g.setLineWidth(1);
                    g.strokeLine(points[i][0] * w, points[i][1] * h, points[j][0] * w, points[j][1] * h);
                }
            }
        }
        for (int i = 0; i < POINTS; i++) {
            double[] p = points[i];
            g.setFill(i % 7 == 0 ? Color.rgb(247, 183, 51, 0.9) : Color.rgb(125, 211, 199, 0.75));
            double size = i % 7 == 0 ? 5 : 3.2;
            g.fillOval(p[0] * w - size / 2, p[1] * h - size / 2, size, size);
        }
    }
}
