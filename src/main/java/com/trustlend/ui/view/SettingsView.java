package com.trustlend.ui.view;

import com.trustlend.config.AppConfig;
import com.trustlend.engine.EngineSettings;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tune the scoring model; values are persisted to data/config.properties. */
public class SettingsView extends BaseView {

    private final Map<String, Slider> weights = new LinkedHashMap<>();
    private final Map<String, Label> weightLabels = new LinkedHashMap<>();
    private final Slider damping = slider(0.30, 0.95, 0.70);
    private final Label dampingLabel = Ui.label("", "strong");
    private final Slider penalty = slider(0.0, 0.40, 0.15);
    private final Label penaltyLabel = Ui.label("", "strong");
    private final Slider interest = slider(10, 30, 18);
    private final Label interestLabel = Ui.label("", "strong");
    private final Slider minScore = slider(300, 750, 500);
    private final Label minScoreLabel = Ui.label("", "strong");
    private final VBox about = new VBox(6);

    public SettingsView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        Button save = new Button("Save & recalculate");
        save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> save());
        Button defaults = new Button("Restore defaults");
        defaults.getStyleClass().add("btn-ghost");
        defaults.setOnAction(e -> loadFrom(EngineSettings.defaults(), 18, 500));
        setHeader("Settings", "Tune how much each signal counts. Weights are normalised to 100% automatically.", defaults, save);

        GridPane weightGrid = grid();
        int row = 0;
        for (String[] f : new String[][]{
                {"consistency", "Transaction consistency"},
                {"repayment", "Repayment history"},
                {"tenure", "Business tenure"},
                {"network", "Community vouches (graph)"}}) {
            Slider s = slider(0, 1, 0.25);
            Label l = Ui.label("", "strong");
            weights.put(f[0], s);
            weightLabels.put(f[0], l);
            s.valueProperty().addListener((o, a, b) -> updateLabels());
            addRow(weightGrid, row++, f[1], s, l);
        }
        VBox weightCard = Ui.titledCard("Factor weights", weightGrid,
                note("The percentages on the right show each factor's share after normalisation."));

        GridPane graphGrid = grid();
        addRow(graphGrid, 0, "Damping factor", damping, dampingLabel);
        addRow(graphGrid, 1, "Voucher penalty per default", penalty, penaltyLabel);
        damping.valueProperty().addListener((o, a, b) -> updateLabels());
        penalty.valueProperty().addListener((o, a, b) -> updateLabels());
        VBox graphCard = Ui.titledCard("Trust propagation", graphGrid,
                note("Damping = how much trust flows along vouches versus staying with a vendor's own record. "
                        + "Higher values make the community's opinion count more."));

        GridPane loanGrid = grid();
        addRow(loanGrid, 0, "Base interest (p.a.)", interest, interestLabel);
        addRow(loanGrid, 1, "Minimum score for a loan", minScore, minScoreLabel);
        interest.valueProperty().addListener((o, a, b) -> updateLabels());
        minScore.valueProperty().addListener((o, a, b) -> updateLabels());
        VBox loanCard = Ui.titledCard("Lending policy", loanGrid,
                note("Rate = base + (650 − score) × 0.02, clamped to 12–30%. Limits by band: Excellent ₹50k, "
                        + "Good ₹25k, Fair ₹12k, Poor ₹5k."));

        VBox aboutCard = Ui.titledCard("About this installation", about);

        HBox top = new HBox(16, weightCard, graphCard);
        HBox bottom = new HBox(16, loanCard, aboutCard);
        for (VBox c : new VBox[]{weightCard, graphCard, loanCard, aboutCard}) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setPrefWidth(480);
        }
        ScrollPane scroll = new ScrollPane(new VBox(16, top, bottom));
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private static Slider slider(double min, double max, double value) {
        Slider s = new Slider(min, max, value);
        s.setPrefWidth(220);
        return s;
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(14);
        ColumnConstraints a = new ColumnConstraints(190);
        ColumnConstraints b = new ColumnConstraints(160, 240, Double.MAX_VALUE);
        b.setHgrow(Priority.ALWAYS);
        ColumnConstraints c = new ColumnConstraints(60);
        c.setHalignment(HPos.RIGHT);
        g.getColumnConstraints().addAll(a, b, c);
        return g;
    }

    private static void addRow(GridPane g, int row, String label, Slider s, Label value) {
        g.addRow(row, Ui.label(label), s, value);
    }

    private static Label note(String text) {
        Label l = Ui.label(text, "muted", "small");
        l.setWrapText(true);
        return l;
    }

    @Override
    public void refresh() {
        AppConfig c = AppConfig.getInstance();
        loadFrom(ctx.scores().getSettings(), c.getDouble("loan.interest.base"), c.getInt("loan.min.score"));
        about.getChildren().setAll(
                info("Java runtime", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"),
                info("JavaFX runtime", String.valueOf(System.getProperty("javafx.runtime.version"))),
                info("Database", c.getPath("db.path").toAbsolutePath().toString()),
                info("User config", AppConfig.USER_CONFIG.toAbsolutePath().toString()),
                info("Vendor objects created this session", String.valueOf(Vendor.getInstancesCreated())),
                info("JVM memory in use", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024) + " MB"));
    }

    private HBox info(String k, String v) {
        Label key = Ui.label(k, "muted", "small");
        key.setMinWidth(210);
        Label val = Ui.label(v, "small");
        val.setWrapText(true);
        return new HBox(8, key, val);
    }

    private void loadFrom(EngineSettings s, double baseInterest, int minLoanScore) {
        weights.get("consistency").setValue(s.wConsistency());
        weights.get("repayment").setValue(s.wRepayment());
        weights.get("tenure").setValue(s.wTenure());
        weights.get("network").setValue(s.wNetwork());
        damping.setValue(s.damping());
        penalty.setValue(s.voucherPenalty());
        interest.setValue(baseInterest);
        minScore.setValue(minLoanScore);
        updateLabels();
    }

    private void updateLabels() {
        double sum = weights.values().stream().mapToDouble(Slider::getValue).sum();
        weights.forEach((k, s) -> weightLabels.get(k).setText(sum == 0 ? "—" : Math.round(s.getValue() / sum * 100) + "%"));
        dampingLabel.setText(String.format("%.2f", damping.getValue()));
        penaltyLabel.setText(Math.round(penalty.getValue() * 100) + "%");
        interestLabel.setText(String.format("%.1f%%", interest.getValue()));
        minScoreLabel.setText(String.valueOf(Math.round(minScore.getValue())));
    }

    private void save() {
        double sum = weights.values().stream().mapToDouble(Slider::getValue).sum();
        if (sum <= 0.0001) {
            shell.toastError("At least one weight must be above zero");
            return;
        }
        AppConfig c = AppConfig.getInstance();
        weights.forEach((k, s) -> c.set("weight." + k, String.format(java.util.Locale.ROOT, "%.3f", s.getValue() / sum)));
        c.set("graph.damping", String.format(java.util.Locale.ROOT, "%.3f", damping.getValue()));
        c.set("graph.voucher.penalty", String.format(java.util.Locale.ROOT, "%.3f", penalty.getValue()));
        c.set("loan.interest.base", String.format(java.util.Locale.ROOT, "%.1f", interest.getValue()));
        c.set("loan.min.score", String.valueOf(Math.round(minScore.getValue())));
        try {
            c.save();
            ctx.scores().reloadSettings();
            shell.recalculateScores(() -> "Settings saved — every score recalculated with the new model");
        } catch (Exception e) {
            Ui.error("Could not save settings", e);
        }
    }
}
