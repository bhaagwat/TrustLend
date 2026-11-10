package com.trustlend.ui;

import com.trustlend.service.StoryScenario;
import com.trustlend.service.StoryScenario.Chapter;
import com.trustlend.service.StoryScenario.Frame;
import com.trustlend.ui.component.Avatar;
import com.trustlend.ui.component.Icons;
import com.trustlend.ui.component.ScoreGauge;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.List;

/**
 * Full-window "Meet Sunil" story. Each chapter plays its frames automatically (the gauge,
 * factor bars, event feed and score chart animate); the presenter moves on with Next,
 * the → key or Space. Esc leaves the story.
 */
public class StoryMode extends StackPane {

    private static final Duration STEP = Duration.millis(1100);

    private final List<Chapter> chapters;
    private final Runnable onExit;

    private final ScoreGauge gauge = new ScoreGauge(240, 300, 900);
    private final ProgressBar[] bars = new ProgressBar[4];
    private final Label[] barValues = new Label[4];
    private final Label chapterNo = Ui.label("", "story-chapter-no");
    private final Label title = Ui.label("", "story-title");
    private final Label narration = Ui.label("", "story-narration");
    private final VBox feed = new VBox(8);
    private final HBox dots = new HBox(8);
    private final Button next = new Button();
    private final Button back = new Button("←  Back");
    private final Label stepLabel = Ui.label("", "story-step");
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();
    private final LineChart<Number, Number> chart;
    private final Pane vouchDiagram = new Pane();
    private final StackPane stage = new StackPane();
    private final VBox outcome = new VBox(10);

    private int chapter = -1;
    private Timeline player;
    private boolean finished;

    public StoryMode(List<Chapter> chapters, Runnable onExit) {
        this.chapters = chapters;
        this.onExit = onExit;
        getStyleClass().add("story");

        // ---- top bar
        Label badge = Ui.label("STORY MODE", "story-badge");
        Label heading = Ui.label("Meet Sunil — from \"no credit history\" to a fair loan", "story-heading");
        for (int i = 0; i < chapters.size(); i++) {
            Region dot = new Region();
            dot.getStyleClass().add("story-dot");
            dots.getChildren().add(dot);
        }
        Button exit = new Button("Exit story", Icons.of("cross", 14));
        exit.getStyleClass().add("story-ghost");
        exit.setOnAction(e -> close());
        HBox top = new HBox(14, Icons.of("play", 22), badge, heading, Ui.hSpacer(), dots, exit);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("story-top");

        // ---- left: the hero
        Label name = Ui.label(StoryScenario.HERO, "story-name");
        Label role = Ui.label("Chai seller · Chandni Chowk, Delhi · 6 years", "story-role");
        VBox factors = new VBox(10);
        String[] labels = {"Consistency", "Repayment", "Tenure", "Community"};
        String[] keys = {"consistency", "repayment", "tenure", "network"};
        for (int i = 0; i < 4; i++) {
            bars[i] = new ProgressBar(0);
            bars[i].getStyleClass().addAll("factor-bar", "factor-" + keys[i]);
            bars[i].setMaxWidth(Double.MAX_VALUE);
            barValues[i] = Ui.label("0%", "story-bar-value");
            HBox head = new HBox(Ui.label(labels[i], "story-bar-label"), Ui.hSpacer(), barValues[i]);
            factors.getChildren().add(new VBox(4, head, bars[i]));
        }
        VBox left = new VBox(12, new Avatar(StoryScenario.HERO, "SY", 64), name, role, gauge, factors);
        left.setAlignment(Pos.TOP_CENTER);
        left.getStyleClass().add("story-panel");
        left.setPrefWidth(340);
        left.setMinWidth(320);

        // ---- centre: chapter text, events, chart
        narration.setWrapText(true);
        title.setWrapText(true);
        NumberAxis x = new NumberAxis();
        x.setTickLabelsVisible(false);
        x.setTickMarkVisible(false);
        x.setMinorTickVisible(false);
        x.setForceZeroInRange(false);
        NumberAxis y = new NumberAxis(300, 900, 100);
        y.setAutoRanging(false);
        chart = new LineChart<>(x, y);
        chart.getData().add(series);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(160);
        chart.setMinHeight(120);
        chart.getStyleClass().add("story-chart");

        vouchDiagram.setPrefSize(300, 190);
        vouchDiagram.setMinSize(300, 190);
        vouchDiagram.setMaxSize(300, 190);
        vouchDiagram.getStyleClass().add("story-diagram");

        feed.setPrefWidth(420);
        HBox.setHgrow(feed, Priority.ALWAYS);
        HBox middle = new HBox(18, feed, vouchDiagram);
        VBox.setVgrow(middle, Priority.ALWAYS);
        VBox centre = new VBox(12, chapterNo, title, narration, middle, Ui.label("TRUST SCORE OVER TIME", "story-chapter-no"), chart);
        centre.getStyleClass().add("story-panel");
        HBox.setHgrow(centre, Priority.ALWAYS);

        outcome.setAlignment(Pos.CENTER);
        outcome.getStyleClass().add("story-outcome");
        outcome.setVisible(false);
        outcome.setMaxSize(560, 320);
        stage.getChildren().addAll(centre, outcome);
        HBox.setHgrow(stage, Priority.ALWAYS);

        HBox body = new HBox(18, left, stage);
        body.setPadding(new Insets(18, 24, 6, 24));

        // ---- bottom bar
        back.getStyleClass().add("story-ghost");
        back.setOnAction(e -> play(chapter - 1));
        Button replay = new Button("↺  Replay chapter");
        replay.getStyleClass().add("story-ghost");
        replay.setOnAction(e -> play(chapter));
        next.getStyleClass().add("btn-accent");
        next.setOnAction(e -> forward());
        Label keysHint = Ui.label("→ / Space: next   ·   ←: back   ·   Esc: exit", "story-step");
        HBox bottom = new HBox(12, back, replay, stepLabel, Ui.hSpacer(), keysHint, next);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.getStyleClass().add("story-bottom");

        BorderPane frame = new BorderPane(body, top, null, bottom, null);
        frame.getStyleClass().add("story-frame");
        getChildren().add(frame);

        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) forward();
            else if (e.getCode() == KeyCode.LEFT && chapter > 0) play(chapter - 1);
            else if (e.getCode() == KeyCode.ESCAPE) close();
            e.consume();
        });
    }

    /** Starts from chapter 1 with a fade-in. */
    public void start() {
        setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(300), this);
        fade.setToValue(1);
        fade.play();
        play(0);
        requestFocus();
    }

    private void forward() {
        if (player != null && player.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            player.jumpTo(player.getTotalDuration());     // skip the animation
            return;
        }
        if (chapter == chapters.size() - 1 && finished) close();
        else play(chapter + 1);
    }

    // ------------------------------------------------------------------ playback

    private void play(int index) {
        if (index < 0 || index >= chapters.size()) return;
        if (player != null) player.stop();
        chapter = index;
        finished = false;
        Chapter c = chapters.get(index);

        for (int i = 0; i < dots.getChildren().size(); i++) {
            Node dot = dots.getChildren().get(i);
            dot.getStyleClass().removeAll("done", "current");
            if (i < index) dot.getStyleClass().add("done");
            if (i == index) dot.getStyleClass().add("current");
        }
        chapterNo.setText("CHAPTER " + (index + 1) + " OF " + chapters.size());
        title.setText(c.title());
        narration.setText(c.narration());
        feed.getChildren().clear();
        outcome.setVisible(false);
        back.setDisable(index == 0);
        next.setText("Skip animation  »");

        // everything before this chapter appears at once
        series.getData().clear();
        int n = 0;
        Frame previous = null;
        for (int i = 0; i < index; i++) {
            for (Frame f : chapters.get(i).frames()) {
                series.getData().add(new XYChart.Data<>(n++, f.score()));
                previous = f;
            }
        }
        if (previous != null) show(previous);
        drawDiagram(index >= 2 ? (index == 2 ? 0 : 3) : -1);

        player = new Timeline();
        int start = n;
        List<Frame> frames = c.frames();
        for (int j = 0; j < frames.size(); j++) {
            Frame f = frames.get(j);
            int step = j;
            player.getKeyFrames().add(new KeyFrame(STEP.multiply(j).add(Duration.millis(350)), e -> {
                show(f);
                series.getData().add(new XYChart.Data<>(start + step, f.score()));
                addEvent(f, step == frames.size() - 1);
                if (chapter == 2) drawDiagram(step + 1);
                stepLabel.setText("Step " + (step + 1) + " of " + frames.size());
            }));
        }
        player.getKeyFrames().add(new KeyFrame(STEP.multiply(frames.size()).add(Duration.millis(400)), e -> chapterDone()));
        player.play();
        requestFocus();
    }

    private void chapterDone() {
        finished = true;
        Chapter c = chapters.get(chapter);
        boolean last = chapter == chapters.size() - 1;
        next.setText(last ? "Finish story  ✓" : "Next chapter  →");
        if (c.outcome() != null) showOutcome(c.outcome());
    }

    private void show(Frame f) {
        gauge.setScore(f.score());
        double[] values = {f.consistency(), f.repayment(), f.tenure(), f.network()};
        for (int i = 0; i < 4; i++) {
            Timeline t = new Timeline(new KeyFrame(Duration.millis(700),
                    new KeyValue(bars[i].progressProperty(), values[i])));
            t.play();
            barValues[i].setText(Math.round(values[i] * 100) + "%");
        }
    }

    private void addEvent(Frame f, boolean highlight) {
        Label text = Ui.label(f.event(), "story-event-text");
        text.setWrapText(true);
        Label score = Ui.label(String.valueOf(f.score()), "story-event-score");
        score.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, Icons.of("check", 16), text, Ui.hSpacer(), score);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("story-event");
        if (highlight) row.getStyleClass().add("story-event-last");
        feed.getChildren().add(row);
        while (feed.getChildren().size() > 4) feed.getChildren().remove(0);
        row.setOpacity(0);
        row.setTranslateX(-16);
        FadeTransition fade = new FadeTransition(Duration.millis(350), row);
        fade.setToValue(1);
        javafx.animation.TranslateTransition slide = new javafx.animation.TranslateTransition(Duration.millis(350), row);
        slide.setToX(0);
        new ParallelTransition(fade, slide).play();
    }

    private void showOutcome(StoryScenario.Outcome o) {
        Label stamp = Ui.label(o.stamp(), "story-stamp", o.approved() ? "approved" : "declined");
        stamp.setRotate(-8);
        Label headline = Ui.label(o.headline(), "story-outcome-headline");
        Label detail = Ui.label(o.detail(), "story-outcome-detail");
        headline.setWrapText(true);
        detail.setWrapText(true);
        outcome.getChildren().setAll(stamp, headline, detail);
        outcome.setVisible(true);
        stamp.setScaleX(3);
        stamp.setScaleY(3);
        stamp.setOpacity(0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(420), stamp);
        scale.setToX(1);
        scale.setToY(1);
        FadeTransition fade = new FadeTransition(Duration.millis(300), stamp);
        fade.setToValue(1);
        outcome.setOpacity(0);
        FadeTransition box = new FadeTransition(Duration.millis(250), outcome);
        box.setToValue(1);
        new ParallelTransition(box, scale, fade).play();
    }

    /** Draws Sunil and the first {@code vouchers} of his three vouchers (-1 hides the diagram). */
    private void drawDiagram(int vouchers) {
        vouchDiagram.getChildren().clear();
        vouchDiagram.setVisible(vouchers >= 0);
        vouchDiagram.setManaged(vouchers >= 0);
        if (vouchers < 0) return;
        double cx = 150;
        double cy = 140;
        String[][] people = {{"MD", "Meena"}, {"FA", "Farooq"}, {"LJ", "Lata"}};
        double[][] pos = {{50, 45}, {150, 30}, {250, 45}};
        for (int i = 0; i < Math.min(vouchers, 3); i++) {
            Line line = new Line(pos[i][0], pos[i][1], pos[i][0], pos[i][1]);
            line.getStyleClass().add("story-edge");
            vouchDiagram.getChildren().add(line);
            boolean animate = chapter == 2 && i == vouchers - 1;
            double ex = cx + (pos[i][0] - cx) * 0.22;
            double ey = cy + (pos[i][1] - cy) * 0.22;
            if (animate) {
                new Timeline(new KeyFrame(Duration.millis(600),
                        new KeyValue(line.endXProperty(), ex), new KeyValue(line.endYProperty(), ey))).play();
            } else {
                line.setEndX(ex);
                line.setEndY(ey);
            }
            vouchDiagram.getChildren().add(node(pos[i][0], pos[i][1], 20, people[i][0], people[i][1], false));
        }
        vouchDiagram.getChildren().add(node(cx, cy, 28, "SY", "Sunil", true));
        Text caption = new Text(10, 185, vouchers == 0 ? "Waiting for vouches…" : vouchers + " of 3 vouches");
        caption.getStyleClass().add("story-diagram-caption");
        vouchDiagram.getChildren().add(caption);
    }

    private Node node(double x, double y, double r, String initials, String name, boolean hero) {
        Circle c = new Circle(x, y, r);
        c.getStyleClass().add(hero ? "story-node-hero" : "story-node");
        Text t = new Text(initials);
        t.getStyleClass().add("story-node-text");
        t.setX(x - t.getLayoutBounds().getWidth() / 2 - 4);
        t.setY(y + 5);
        Text label = new Text(name);
        label.getStyleClass().add("story-node-label");
        label.setX(x - name.length() * 3.2);
        label.setY(y + r + 15);
        return new javafx.scene.Group(c, t, label);
    }

    private void close() {
        if (player != null) player.stop();
        FadeTransition fade = new FadeTransition(Duration.millis(220), this);
        fade.setToValue(0);
        fade.setOnFinished(e -> onExit.run());
        fade.play();
    }
}
