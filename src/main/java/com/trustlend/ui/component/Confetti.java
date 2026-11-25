package com.trustlend.ui.component;

import javafx.animation.AnimationTimer;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A short burst of falling confetti over any {@link StackPane} — for approvals and happy endings.
 * Simple particle physics on an {@link AnimationTimer}: each piece has a velocity, gravity,
 * air drag, a sideways wobble and a spin. The layer removes itself when the burst is over.
 */
public final class Confetti extends Pane {

    private static final Color[] COLORS = {
            Color.web("#f29f05"), Color.web("#0f9d58"), Color.web("#1a56db"),
            Color.web("#d93025"), Color.web("#8b5cf6"), Color.web("#14b8a6"), Color.web("#f7b733")};

    private record Piece(Rectangle shape, double[] state) { }   // x, y, vx, vy, spin, wobblePhase

    private final List<Piece> pieces = new ArrayList<>();
    private final Random random = new Random();
    private final AnimationTimer timer;
    private long start = -1;
    private long last = -1;

    private Confetti(double width, double height, int count) {
        setMouseTransparent(true);
        setPickOnBounds(false);
        setManaged(false);
        resize(width, height);
        for (int i = 0; i < count; i++) {
            Rectangle r = new Rectangle(6 + random.nextDouble() * 6, 8 + random.nextDouble() * 8,
                    COLORS[random.nextInt(COLORS.length)]);
            r.setArcWidth(2);
            r.setArcHeight(2);
            // two cannons, bottom-left and bottom-right, firing up and inwards
            boolean left = i % 2 == 0;
            double x = left ? width * 0.08 : width * 0.92;
            double y = height * 0.95;
            double angle = Math.toRadians(left ? -60 - random.nextDouble() * 25 : -120 + random.nextDouble() * 25);
            double speed = height * (1.05 + random.nextDouble() * 0.75);
            double[] s = {x, y, Math.cos(angle) * speed, Math.sin(angle) * speed,
                    (random.nextDouble() - 0.5) * 720, random.nextDouble() * Math.PI * 2};
            pieces.add(new Piece(r, s));
            getChildren().add(r);
        }
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (start < 0) {
                    start = now;
                    last = now;
                }
                double dt = Math.min(0.05, (now - last) / 1e9);
                last = now;
                double age = (now - start) / 1e9;
                double gravity = height * 1.4;
                for (Piece p : pieces) {
                    double[] s = p.state();
                    s[3] += gravity * dt;
                    s[2] *= 1 - 1.2 * dt;            // air drag
                    s[3] *= 1 - 1.2 * dt;
                    s[5] += dt * 6;
                    s[0] += (s[2] + Math.sin(s[5]) * 40) * dt;
                    s[1] += s[3] * dt;
                    p.shape().setTranslateX(s[0]);
                    p.shape().setTranslateY(s[1]);
                    p.shape().setRotate(p.shape().getRotate() + s[4] * dt);
                    p.shape().setScaleX(Math.cos(s[5]));     // fake 3-D flutter
                }
                if (age > 2.6) setOpacity(Math.max(0, 1 - (age - 2.6) / 0.8));
                if (age > 3.4) {
                    stop();
                    if (getParent() instanceof Pane parent) parent.getChildren().remove(Confetti.this);
                }
            }
        };
    }

    /** Fires a burst of confetti over {@code host}. Safe to call several times. */
    public static void burst(StackPane host) {
        double w = host.getWidth() > 0 ? host.getWidth() : 800;
        double h = host.getHeight() > 0 ? host.getHeight() : 600;
        Confetti c = new Confetti(w, h, 140);
        host.getChildren().add(c);
        c.timer.start();
    }
}
