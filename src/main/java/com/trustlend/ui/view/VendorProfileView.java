package com.trustlend.ui.view;

import com.trustlend.model.Loan;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.Vouch;
import com.trustlend.service.AppContext;
import com.trustlend.service.LoanService;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Avatar;
import com.trustlend.ui.component.FactorBar;
import com.trustlend.ui.component.ScoreGauge;
import com.trustlend.ui.dialog.LoanDialog;
import com.trustlend.ui.dialog.TransactionDialog;
import com.trustlend.ui.dialog.VouchDialog;
import com.trustlend.util.Formats;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Everything about one vendor: animated score gauge, explainable breakdown, history and relationships. */
public class VendorProfileView extends BaseView {

    private final long vendorId;
    private Vendor vendor;

    private final ScoreGauge gauge;
    private final Label offerLabel = Ui.label("", "small");
    private final Label rankLabel = Ui.label("", "muted", "small");
    private final FactorBar consistencyBar;
    private final FactorBar repaymentBar;
    private final FactorBar tenureBar;
    private final FactorBar networkBar;
    private final VBox reasons = new VBox(6);

    private final LineChart<String, Number> historyChart = new LineChart<>(new CategoryAxis(), new NumberAxis(300, 900, 100));
    private final BarChart<String, Number> salesChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final ListView<Vouch> received = new ListView<>();
    private final ListView<Vouch> given = new ListView<>();
    private final TableView<Loan> loanTable = new TableView<>();
    private final TableView<Transaction> txnTable = new TableView<>();
    private final Map<Long, Vendor> vendorsById = new HashMap<>();

    public VendorProfileView(AppContext ctx, MainWindow shell, long vendorId) {
        super(ctx, shell);
        this.vendorId = vendorId;
        var settings = ctx.scores().getSettings();
        gauge = new ScoreGauge(230, settings.minScore(), settings.maxScore());
        consistencyBar = new FactorBar("Transaction consistency", "consistency", settings.wConsistency(),
                "Regularity and stability of weekly sales over the last 12 weeks");
        repaymentBar = new FactorBar("Repayment history", "repayment", settings.wRepayment(),
                "On-time instalments, repaid loans, overdue and defaulted loans");
        tenureBar = new FactorBar("Business tenure", "tenure", settings.wTenure(),
                "Years trading plus months on TrustLend");
        networkBar = new FactorBar("Community trust", "network", settings.wNetwork(),
                "Trust flowing in from vouching vendors (weighted by their own trust)");

        // ---- score card
        offerLabel.setWrapText(true);
        offerLabel.setMaxWidth(240);
        VBox scoreCard = Ui.card(gauge, rankLabel, offerLabel);
        scoreCard.setAlignment(Pos.TOP_CENTER);
        scoreCard.setMinWidth(280);
        scoreCard.setMaxWidth(280);

        // ---- breakdown card
        VBox breakdown = Ui.titledCard("Score breakdown", consistencyBar, repaymentBar, tenureBar, networkBar);
        HBox.setHgrow(breakdown, Priority.ALWAYS);
        breakdown.setPrefWidth(360);

        VBox why = Ui.titledCard("Why this score", reasons);
        HBox.setHgrow(why, Priority.ALWAYS);
        why.setPrefWidth(380);

        HBox top = new HBox(16, scoreCard, breakdown, why);

        // ---- tabs
        historyChart.setLegendVisible(false);
        historyChart.setAnimated(false);
        historyChart.setPrefHeight(260);
        salesChart.setLegendVisible(false);
        salesChart.setAnimated(false);
        salesChart.setPrefHeight(260);
        salesChart.getYAxis().setLabel("₹ sales per week");

        received.setCellFactory(lv -> new VouchCell(true));
        given.setCellFactory(lv -> new VouchCell(false));
        received.setPrefHeight(240);
        given.setPrefHeight(240);
        Button addVouch = new Button("+ Add vouch");
        addVouch.getStyleClass().add("btn-ghost");
        addVouch.setOnAction(e -> openVouchDialog());
        Button removeVouch = new Button("Remove selected");
        removeVouch.getStyleClass().add("btn-danger");
        removeVouch.setOnAction(e -> removeSelectedVouch());
        VBox recBox = new VBox(6, Ui.label("Vouched for by", "section-title"), received);
        VBox givBox = new VBox(6, Ui.label("Vouches for", "section-title"), given);
        HBox.setHgrow(recBox, Priority.ALWAYS);
        HBox.setHgrow(givBox, Priority.ALWAYS);
        VBox vouchTab = new VBox(10, new HBox(16, recBox, givBox), Ui.row(8, addVouch, removeVouch));
        vouchTab.setPadding(new Insets(14));

        buildLoanTable();
        buildTxnTable();

        TabPane tabs = new TabPane(
                tab("Score history", padded(historyChart)),
                tab("Weekly sales", padded(salesChart)),
                tab("Vouches", vouchTab),
                tab("Loans", padded(loanTable)),
                tab("Transactions", padded(txnTable)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox tabCard = Ui.card(tabs);
        tabCard.setPadding(new Insets(6));

        VBox content = new VBox(16, top, tabCard);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private static Tab tab(String title, javafx.scene.Node node) {
        return new Tab(title, node);
    }

    private static VBox padded(javafx.scene.Node node) {
        VBox box = new VBox(node);
        box.setPadding(new Insets(12));
        VBox.setVgrow(node, Priority.ALWAYS);
        box.setPrefHeight(300);
        return box;
    }

    private void buildHeader() {
        Button back = new Button("←");
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(e -> shell.navigate(MainWindow.Page.VENDORS));
        Button txn = new Button("Record transaction");
        txn.getStyleClass().add("btn-ghost");
        txn.setOnAction(e -> new TransactionDialog(ctx, List.of(vendor), vendor).showAndWait()
                .ifPresent(t -> shell.recalculateScores(() -> "Transaction recorded for " + vendor.getName())));
        Button network = new Button("View in network");
        network.getStyleClass().add("btn-ghost");
        network.setOnAction(e -> shell.openNetworkAround(vendorId));
        Button loan = new Button("₹  New loan");
        loan.getStyleClass().add("btn-accent");
        loan.setOnAction(e -> new LoanDialog(ctx, List.of(vendor), vendor).showAndWait()
                .ifPresent(l -> shell.recalculateScores(() -> "Loan #" + l.getId() + " issued to " + vendor.getName())));

        Avatar avatar = new Avatar(vendor.getName(), vendor.getInitials(), 46);
        VBox text = new VBox(2, Ui.label(vendor.getName() + (vendor.isActive() ? "" : "  (inactive)"), "page-title"),
                Ui.label(vendor.describeRole() + "  ·  +91 " + vendor.getPhone(), "page-subtitle"));
        HBox header = new HBox(12, back, avatar, text, Ui.hSpacer(), txn, network, loan);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 20, 0));
        setTop(header);
    }

    @Override
    public void refresh() {
        try {
            vendor = ctx.vendors().get(vendorId);
            vendorsById.clear();
            List<Vendor> all = ctx.vendors().ranked();
            for (Vendor v : all) vendorsById.put(v.getId(), v);
            buildHeader();

            ScoreBreakdown s = ctx.scores().scoreOf(vendorId).orElse(null);
            if (s != null) {
                gauge.setScore(s.score());
                consistencyBar.setValue(s.consistency());
                repaymentBar.setValue(s.repayment());
                tenureBar.setValue(s.tenure());
                networkBar.setValue(s.network());
                reasons.getChildren().clear();
                for (String r : s.reasons()) {
                    boolean negative = r.contains("defaulted") || r.contains("overdue") || r.contains("No ")
                            || r.contains("swing") || r.contains("capped") || r.contains("provisional");
                    Label icon = Ui.label(negative ? "▼" : "▲", "reason-icon");
                    icon.setStyle("-fx-text-fill: " + (negative ? "#d93025" : "#0f9d58") + ";");
                    Label text = Ui.label(r, "reason");
                    text.setWrapText(true);
                    HBox row = new HBox(8, icon, text);
                    row.setAlignment(Pos.TOP_LEFT);
                    reasons.getChildren().add(row);
                }
            }
            int rank = all.indexOf(vendor) + 1;
            rankLabel.setText("Ranked #" + rank + " of " + all.size() + " vendors");

            LoanService.Offer offer = ctx.loans().offerFor(vendorId);
            offerLabel.setText(offer.note());
            offerLabel.setStyle(offer.maxAmount() > 0
                    ? "-fx-background-color: #dff3f0; -fx-text-fill: #0b5f58; -fx-padding: 8; -fx-background-radius: 8;"
                    : "-fx-background-color: #fff3db; -fx-text-fill: #7a5200; -fx-padding: 8; -fx-background-radius: 8;");

            fillHistory();
            fillSales();
            received.getItems().setAll(ctx.vouches().received(vendorId));
            given.getItems().setAll(ctx.vouches().given(vendorId));
            loanTable.getItems().setAll(ctx.loans().loansOf(vendorId));
            txnTable.getItems().setAll(ctx.transactions().forVendor(vendorId));
        } catch (Exception e) {
            Ui.error("Could not load vendor", e);
        }
    }

    private void fillHistory() throws Exception {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM");
        for (ScoreBreakdown h : ctx.scores().history(vendorId)) {
            series.getData().add(new XYChart.Data<>(f.format(h.computedOn()), h.score()));
        }
        historyChart.getData().setAll(List.of(series));
    }

    private void fillSales() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusWeeks(12).plusDays(1);
        double[] weeks = new double[12];
        for (Transaction t : ctx.transactions().forVendorBetween(vendorId, start, today)) {
            if (t.getType() == Transaction.Type.SALE) {
                int w = (int) (ChronoUnit.DAYS.between(start, t.getDate()) / 7);
                if (w >= 0 && w < 12) weeks[w] += t.getAmount();
            }
        }
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM");
        for (int i = 0; i < 12; i++) {
            XYChart.Data<String, Number> d = new XYChart.Data<>(f.format(start.plusWeeks(i)), Math.round(weeks[i]));
            d.nodeProperty().addListener((o, a, node) -> {
                if (node != null) node.setStyle("-fx-bar-fill: #3b82f6;");
            });
            series.getData().add(d);
        }
        salesChart.getData().setAll(List.of(series));
    }

    private void buildLoanTable() {
        loanTable.getColumns().add(col("LOAN", l -> "#" + l.getId()));
        loanTable.getColumns().add(col("PRINCIPAL", l -> Formats.inr(l.getPrincipal())));
        loanTable.getColumns().add(col("RATE", l -> l.getInterestRate() + "%"));
        loanTable.getColumns().add(col("ISSUED", l -> Formats.date(l.getIssueDate())));
        loanTable.getColumns().add(col("DUE", l -> Formats.date(l.getDueDate())));
        loanTable.getColumns().add(col("REPAID", l -> Formats.inr(l.getAmountRepaid()) + " / " + Formats.inr(l.getTotalPayable())));
        TableColumn<Loan, Loan> status = new TableColumn<>("STATUS");
        status.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        status.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Loan l, boolean empty) {
                super.updateItem(l, empty);
                setGraphic(empty || l == null ? null : Ui.statusPill(l.getStatus()));
            }
        });
        loanTable.getColumns().add(status);
        loanTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        loanTable.setPlaceholder(new Label("No loans yet"));
    }

    private void buildTxnTable() {
        txnTable.getColumns().add(txCol("DATE", t -> Formats.date(t.getDate())));
        txnTable.getColumns().add(txCol("TYPE", t -> t.getType().getLabel()));
        txnTable.getColumns().add(txCol("AMOUNT", t -> Formats.inr(t.getAmount())));
        txnTable.getColumns().add(txCol("NOTE", t -> t.getNote() == null ? "" : t.getNote()));
        txnTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        txnTable.setPlaceholder(new Label("No transactions recorded"));
    }

    private static TableColumn<Loan, String> col(String title, Function<Loan, String> f) {
        TableColumn<Loan, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(f.apply(cell.getValue())));
        return c;
    }

    private static TableColumn<Transaction, String> txCol(String title, Function<Transaction, String> f) {
        TableColumn<Transaction, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(f.apply(cell.getValue())));
        return c;
    }

    private void openVouchDialog() {
        new VouchDialog(ctx, List.copyOf(vendorsById.values()), vendor).showAndWait()
                .ifPresent(v -> shell.recalculateScores(() -> "Vouch recorded — trust re-propagated"));
    }

    private void removeSelectedVouch() {
        Vouch v = received.getSelectionModel().getSelectedItem();
        if (v == null) v = given.getSelectionModel().getSelectedItem();
        if (v == null) {
            shell.toastInfo("Select a vouch in either list first");
            return;
        }
        if (!Ui.confirm("Remove vouch", "Remove this vouch? Scores will be recalculated.", "Remove")) return;
        try {
            ctx.vouches().removeVouch(v.getId());
            shell.recalculateScores(() -> "Vouch removed");
        } catch (Exception e) {
            Ui.error("Could not remove vouch", e);
        }
    }

    /** List cell showing the other vendor, their score, relationship and strength as dots. */
    private class VouchCell extends ListCell<Vouch> {
        private final boolean incoming;

        VouchCell(boolean incoming) {
            this.incoming = incoming;
        }

        @Override
        protected void updateItem(Vouch v, boolean empty) {
            super.updateItem(v, empty);
            if (empty || v == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Vendor other = vendorsById.get(incoming ? v.getVoucherId() : v.getVoucheeId());
            String name = other == null ? "Unknown" : other.getName();
            VBox text = new VBox(1, Ui.label(name, "strong"),
                    Ui.label(v.getRelationship().getLabel() + " · since " + Formats.date(v.getCreatedDate()), "muted", "small"));
            Label strength = Ui.label("●".repeat(v.getStrength()) + "○".repeat(Vouch.MAX_STRENGTH - v.getStrength()));
            strength.setStyle("-fx-text-fill: #f29f05; -fx-font-size: 12px;");
            HBox row = Ui.row(10, new Avatar(name, other == null ? "?" : other.getInitials(), 28), text, Ui.hSpacer(), strength);
            if (other != null) row.getChildren().add(Ui.bandPill(other.getRiskBand()));
            setGraphic(row);
            setText(null);
        }
    }
}
