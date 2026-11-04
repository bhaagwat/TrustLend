package com.trustlend.ui.view;

import com.trustlend.engine.EngineSettings;
import com.trustlend.model.RiskBand;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.service.AnalyticsService;
import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Avatar;
import com.trustlend.ui.component.StatCard;
import com.trustlend.util.Formats;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Portfolio overview: KPI tiles, score distribution, risk mix, top vendors and recent activity. */
public class DashboardView extends BaseView {

    private final StatCard vendorsCard = new StatCard("Registered vendors", "#0f766e");
    private final StatCard scoreCard = new StatCard("Average trust score", "#f29f05");
    private final StatCard loansCard = new StatCard("Outstanding loan book", "#1a56db");
    private final StatCard repayCard = new StatCard("On-time instalments", "#8b5cf6");

    private final BarChart<String, Number> histogram = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final PieChart bandPie = new PieChart();
    private final VBox topList = new VBox(6);
    private final VBox activityList = new VBox(4);
    private final VBox factorList = new VBox(10);

    public DashboardView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        Button recalc = new Button("↻  Recalculate scores");
        recalc.getStyleClass().add("btn-accent");
        recalc.setOnAction(e -> shell.recalculateScores(() -> "Trust scores recalculated for all vendors"));
        setHeader(greeting(), "Portfolio health and community trust at a glance — " + Formats.date(LocalDate.now()), recalc);

        HBox kpis = new HBox(16, vendorsCard, scoreCard, loansCard, repayCard);

        histogram.setLegendVisible(false);
        histogram.setAnimated(false);
        histogram.setCategoryGap(6);
        histogram.setPrefHeight(270);
        histogram.getYAxis().setLabel("Vendors");
        VBox histCard = Ui.titledCard("Trust score distribution", histogram);
        HBox.setHgrow(histCard, Priority.ALWAYS);

        bandPie.setLegendVisible(true);
        bandPie.setLabelsVisible(false);
        bandPie.setPrefHeight(270);
        bandPie.setAnimated(false);
        bandPie.getStyleClass().add("bands-pie");
        VBox pieCard = Ui.titledCard("Risk bands", bandPie);
        pieCard.setPrefWidth(360);
        pieCard.setMinWidth(320);

        HBox charts = new HBox(16, histCard, pieCard);

        VBox topCard = Ui.titledCard("Most trusted vendors", topList);
        VBox factorsCard = Ui.titledCard("How the score is built", factorList);
        VBox activityCard = Ui.titledCard("Latest activity", activityList);
        for (VBox c : List.of(topCard, factorsCard, activityCard)) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setPrefWidth(320);
        }
        HBox bottom = new HBox(16, topCard, factorsCard, activityCard);

        VBox content = new VBox(18, kpis, charts, bottom);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
    }

    @Override
    public void refresh() {
        try {
            AnalyticsService.Stats s = ctx.analytics().compute();
            int healthy = s.bandCounts().get(RiskBand.EXCELLENT) + s.bandCounts().get(RiskBand.GOOD);
            vendorsCard.update(String.valueOf(s.vendors()), healthy + " rated Good or better · " + s.vouches() + " vouches");
            RiskBand avgBand = RiskBand.fromScore((int) Math.round(s.averageScore()));
            scoreCard.update(String.valueOf(Math.round(s.averageScore())), "Portfolio average is " + avgBand.getLabel());
            loansCard.update(Formats.inrCompact(s.outstanding()), s.openLoans() + " open loans · "
                    + Formats.inrCompact(s.totalDisbursed()) + " disbursed to date");
            repayCard.update(Formats.percent(s.onTimeRate()), s.defaulted() + " defaulted loan(s) in portfolio");

            fillHistogram(s.scoreHistogram());
            fillPie(s.bandCounts());
            fillTop(s.topVendors());
            fillFactors(ctx.scores().getSettings());
            fillActivity();
        } catch (Exception e) {
            Ui.error("Could not load dashboard", e);
        }
    }

    private void fillHistogram(int[] buckets) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < buckets.length; i++) {
            int from = 300 + i * 50;
            XYChart.Data<String, Number> d = new XYChart.Data<>(String.valueOf(from), buckets[i]);
            String color = RiskBand.fromScore(from).getColorHex();
            // Bar nodes are created lazily by the chart, so colour them when they appear
            d.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) {
                    node.setStyle("-fx-bar-fill: " + color + ";");
                    Tooltip.install(node, new Tooltip(from + "–" + (from + 49) + ": " + d.getYValue() + " vendor(s)"));
                }
            });
            series.getData().add(d);
        }
        histogram.getData().setAll(List.of(series));
    }

    /**
     * Always adds the five bands in the same order, so the chart assigns them
     * default-color0..4, which app.css maps to the band colours (slice AND legend).
     */
    private void fillPie(Map<RiskBand, Integer> counts) {
        bandPie.getData().clear();
        for (RiskBand b : RiskBand.values()) {
            int n = counts.getOrDefault(b, 0);
            bandPie.getData().add(new PieChart.Data(b.getLabel() + " (" + n + ")", n));
        }
    }

    private void fillTop(List<Vendor> top) {
        topList.getChildren().clear();
        int rank = 1;
        for (Vendor v : top) {
            Label r = Ui.label(String.valueOf(rank++), "muted", "strong");
            r.setMinWidth(16);
            VBox who = new VBox(1, Ui.label(v.getName(), "strong"),
                    Ui.label(v.getCategory().getLabel() + " · " + v.getMarketArea(), "muted", "small"));
            Label score = Ui.label(String.valueOf(v.getCurrentScore()), "pill", v.getRiskBand().styleClass());
            HBox row = Ui.row(10, r, new Avatar(v.getName(), v.getInitials(), 32), who, Ui.hSpacer(), score);
            row.setStyle("-fx-cursor: hand; -fx-padding: 4 0 4 0;");
            row.setOnMouseClicked(e -> shell.openVendor(v.getId()));
            topList.getChildren().add(row);
        }
    }

    private void fillFactors(EngineSettings s) {
        factorList.getChildren().setAll(
                factorRow("#3b82f6", "Transaction consistency", s.wConsistency(), "Regular sales week after week"),
                factorRow("#0f766e", "Repayment history", s.wRepayment(), "On-time instalments, no defaults"),
                factorRow("#8b5cf6", "Business tenure", s.wTenure(), "Years trading and time on platform"),
                factorRow("#f29f05", "Community vouches", s.wNetwork(), "Trust flowing in through the vouch graph"));
        Label note = Ui.label("Score = 300 + 600 × weighted sum. A defaulted loan caps it below 550.", "muted", "small");
        note.setWrapText(true);
        factorList.getChildren().add(note);
    }

    private HBox factorRow(String color, String name, double weight, String desc) {
        Label dot = new Label();
        dot.getStyleClass().add("legend-dot");
        dot.setStyle("-fx-background-color: " + color + ";");
        VBox text = new VBox(1, Ui.label(name, "strong"), Ui.label(desc, "muted", "small"));
        Label pct = Ui.label(Math.round(weight * 100) + "%", "chip");
        HBox row = Ui.row(10, dot, text, Ui.hSpacer(), pct);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void fillActivity() throws Exception {
        activityList.getChildren().clear();
        Map<Long, String> names = new HashMap<>();
        for (Vendor v : ctx.vendors().findAll()) names.put(v.getId(), v.getName());
        for (Transaction t : ctx.transactions().recent(7)) {
            String sign = t.getType() == Transaction.Type.LOAN_DISBURSAL ? "₹ " : "";
            VBox text = new VBox(1, Ui.label(names.getOrDefault(t.getVendorId(), "?"), "strong"),
                    Ui.label(sign + t.getType().getLabel() + " · " + Formats.date(t.getDate()), "muted", "small"));
            HBox row = Ui.row(10, text, Ui.hSpacer(), Ui.label(Formats.inr(t.getAmount()), "strong"));
            activityList.getChildren().add(row);
        }
    }
}
