package com.trustlend.ui.component;

import com.trustlend.model.RiskBand;
import com.trustlend.service.AnalyticsService;
import com.trustlend.ui.Ui;
import javafx.animation.ScaleTransition;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * One tile per market area, coloured by the market's average trust band, with a stacked bar of
 * how its vendors are spread across the bands. Clicking a tile shows that market on the network.
 */
public class MarketHeatmap extends HBox {

    private final Consumer<String> onOpen;

    public MarketHeatmap(Consumer<String> onOpen) {
        super(14);
        this.onOpen = onOpen;
    }

    public void update(List<AnalyticsService.MarketStat> markets) {
        getChildren().clear();
        for (AnalyticsService.MarketStat m : markets) {
            RiskBand band = RiskBand.fromScore((int) Math.round(m.averageScore()));
            Label name = Ui.label(m.market(), "heat-market");
            Label score = Ui.label(String.valueOf(Math.round(m.averageScore())), "heat-score");
            score.setMinWidth(Region.USE_PREF_SIZE);
            Label pill = Ui.label(band.getLabel(), "pill", band.styleClass());
            Label count = Ui.label(m.vendors() + " vendors", "heat-sub");
            Label top = Ui.label("Top: " + m.topVendor(), "heat-sub");

            HBox bar = new HBox();
            bar.getStyleClass().add("heat-bar");
            for (RiskBand b : RiskBand.values()) {
                int n = m.bands().getOrDefault(b, 0);
                if (n == 0) continue;
                Region seg = new Region();
                seg.setStyle("-fx-background-color: " + b.getColorHex() + ";");
                seg.setMinWidth(0);
                seg.prefWidthProperty().bind(bar.widthProperty().multiply(n / (double) m.vendors()));   // share of vendors
                Tooltip.install(seg, new Tooltip(n + " " + b.getLabel()));
                bar.getChildren().add(seg);
            }

            VBox tile = new VBox(6, name, Ui.row(8, score, pill), bar, count, top);
            tile.getStyleClass().addAll("heat-tile", "heat-" + band.name().toLowerCase().replace('_', '-'));
            tile.setPrefWidth(200);
            tile.setMinWidth(160);
            HBox.setHgrow(tile, Priority.ALWAYS);
            tile.setMaxWidth(Double.MAX_VALUE);
            Tooltip.install(tile, new Tooltip("Open " + m.market() + " on the trust network"));
            tile.setOnMouseEntered(e -> grow(tile, 1.04));
            tile.setOnMouseExited(e -> grow(tile, 1.0));
            tile.setOnMouseClicked(e -> onOpen.accept(m.market()));
            getChildren().add(tile);
        }
    }

    private static void grow(Region r, double to) {
        ScaleTransition s = new ScaleTransition(Duration.millis(140), r);
        s.setToX(to);
        s.setToY(to);
        s.play();
    }
}
