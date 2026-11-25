package com.trustlend.ui.component;

import com.trustlend.service.AnalyticsService;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.DoubleFunction;

/**
 * The "why this matters" strip at the top of the dashboard: money lent, vendors financed,
 * on-time instalments and the interest borrowers saved compared with moneylenders.
 * The numbers count up whenever the dashboard is opened.
 */
public class ImpactBanner extends HBox {

    private final Metric lent = new Metric("lent to street vendors", Formats::inrCompact);
    private final Metric financed = new Metric("vendors financed without paperwork", v -> String.valueOf(Math.round(v)));
    private final Metric saved = new Metric("interest saved vs. moneylenders", Formats::inrCompact);
    private final Metric onTime = new Metric("instalments paid on time", v -> String.format("%,d", Math.round(v)));

    public ImpactBanner() {
        super(28);
        getStyleClass().add("impact-banner");
        setAlignment(Pos.CENTER_LEFT);
        Label kicker = Ui.label("OUR IMPACT", "impact-kicker");
        Label title = Ui.label("Fair credit for people\nbanks can't see", "impact-title");
        Label note = Ui.label("Moneylenders charge about " + Math.round(AnalyticsService.MONEYLENDER_RATE) + "% a year.", "impact-note");
        VBox intro = new VBox(4, kicker, title, note);
        intro.setMinWidth(230);
        HBox metrics = new HBox(10, lent, divider(), financed, divider(), saved, divider(), onTime);
        metrics.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(metrics, Priority.ALWAYS);
        for (Metric m : new Metric[]{lent, financed, saved, onTime}) HBox.setHgrow(m, Priority.ALWAYS);
        getChildren().addAll(Icons.of("spark", 40), intro, metrics);
    }

    private static Region divider() {
        Region r = new Region();
        r.getStyleClass().add("impact-divider");
        return r;
    }

    public void update(AnalyticsService.Impact impact) {
        boolean any = impact.loans() > 0;
        setVisible(any);
        setManaged(any);
        if (!any) return;
        lent.animateTo(impact.lent());
        financed.animateTo(impact.vendorsFinanced());
        saved.animateTo(impact.interestSaved());
        onTime.animateTo(impact.onTimePayments());
    }

    /** One big counting number with a caption. */
    private static final class Metric extends VBox {
        private final DoubleProperty shown = new SimpleDoubleProperty();
        private final Label value = Ui.label("0", "impact-value");

        Metric(String caption, DoubleFunction<String> format) {
            super(2);
            Label c = Ui.label(caption, "impact-caption");
            c.setWrapText(true);
            value.setMinWidth(Region.USE_PREF_SIZE);
            getChildren().addAll(value, c);
            setMinWidth(120);
            setPrefWidth(170);
            shown.addListener((o, a, b) -> value.setText(format.apply(b.doubleValue())));
        }

        void animateTo(double target) {
            shown.set(0);
            new Timeline(new KeyFrame(Duration.millis(1400),
                    new KeyValue(shown, target, Interpolator.EASE_OUT))).play();
        }
    }
}
