package com.trustlend.ui.view;

import com.trustlend.io.StatementImporter;
import com.trustlend.model.IncomeDeclaration;
import com.trustlend.model.Upload;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.service.DemoAccounts;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Confetti;
import com.trustlend.ui.component.Icons;
import com.trustlend.util.Formats;
import com.trustlend.util.Validators;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

/**
 * Borrower: prove your business yourself. Upload a UPI / PhonePe / Google Pay / bank statement
 * (checked automatically for tampering) and declare your income (verified later by a lender or HQ).
 */
public class UploadView extends BaseView {

    private final VBox result = new VBox(10);
    private final TableView<Upload> uploads = new TableView<>();
    private final TextField income = new TextField();
    private final ComboBox<IncomeDeclaration.Kind> kind = new ComboBox<>(FXCollections.observableArrayList(IncomeDeclaration.Kind.values()));
    private final Label proofLabel = Ui.label("No file attached (optional)", "muted", "small");
    private File proof;
    private final VBox incomes = new VBox(8);

    public UploadView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        setHeader("Upload proof", "No paperwork? Your phone already has your business history. Upload it here.");

        // ---- statement card
        Label how = Ui.label("Export your statement as CSV from PhonePe, Google Pay, Paytm or your bank, then upload it. "
                + "Money you RECEIVED counts as sales; payments you made are ignored. TrustLend checks the file for "
                + "tampering (copied rows, round amounts, sudden spikes, future dates) before it counts.", "muted", "small");
        how.setWrapText(true);
        Button choose = new Button("Choose statement (CSV)…", Icons.of("upload", 16));
        choose.getStyleClass().add("btn-accent");
        choose.setOnAction(e -> chooseStatement());
        Button sampleGood = new Button("Save a sample statement");
        sampleGood.getStyleClass().add("btn-ghost");
        sampleGood.setOnAction(e -> saveSample(false));
        Button sampleBad = new Button("Save a suspicious sample");
        sampleBad.getStyleClass().add("btn-ghost");
        sampleBad.setOnAction(e -> saveSample(true));
        Label samples = Ui.label("No statement handy? Save a sample (dated up to today) and upload it — "
                + "try the suspicious one to see the fraud checks.", "muted", "small");
        samples.setWrapText(true);
        buildTable();
        VBox statementCard = Ui.titledCard("Sales / UPI statement", how, Ui.row(10, choose), result, samples,
                Ui.row(8, sampleGood, sampleBad), Ui.label("YOUR UPLOADS", "form-label"), uploads);
        HBox.setHgrow(statementCard, Priority.ALWAYS);
        statementCard.setPrefWidth(620);

        // ---- income card
        income.setPromptText("Monthly amount in ₹");
        income.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().matches("[0-9,]{0,9}") ? c : null));
        kind.setValue(IncomeDeclaration.Kind.BUSINESS);
        kind.setMaxWidth(Double.MAX_VALUE);
        Button attach = new Button("Attach salary slip / proof…");
        attach.getStyleClass().add("btn-ghost");
        attach.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Salary slip, bank statement or other proof");
            fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Documents and images", "*.pdf", "*.jpg", "*.jpeg", "*.png"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f != null) {
                proof = f;
                proofLabel.setText("Attached: " + f.getName());
            }
        });
        Button declare = new Button("Declare income");
        declare.getStyleClass().add("btn-primary");
        declare.setOnAction(e -> declareIncome());
        Label incomeHow = Ui.label("Self-declared income helps a little. Once a lender or HQ checks your salary slip "
                + "or bank statement and verifies it, it counts fully.", "muted", "small");
        incomeHow.setWrapText(true);
        VBox incomeCard = Ui.titledCard("Monthly income", incomeHow, Ui.label("AMOUNT PER MONTH", "form-label"), income,
                Ui.label("TYPE", "form-label"), kind, attach, proofLabel, declare,
                Ui.label("YOUR DECLARATIONS", "form-label"), incomes);
        incomeCard.setPrefWidth(360);
        incomeCard.setMinWidth(320);

        HBox body = new HBox(16, statementCard, incomeCard);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private long me() {
        return shell.user().getVendorId();
    }

    private void buildTable() {
        TableColumn<Upload, String> date = new TableColumn<>("UPLOADED");
        date.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(Formats.date(c.getValue().getUploadedOn())));
        TableColumn<Upload, String> file = new TableColumn<>("FILE");
        file.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getFileName()));
        TableColumn<Upload, String> days = new TableColumn<>("DAYS OF SALES");
        days.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getSalesDays() + " · " + Formats.inr(c.getValue().getTotalSales())));
        TableColumn<Upload, String> quality = new TableColumn<>("QUALITY");
        quality.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getQuality() + "/100"));
        TableColumn<Upload, String> status = new TableColumn<>("STATUS");
        status.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStatus().getLabel()));
        uploads.getColumns().addAll(List.of(date, file, days, quality, status));
        uploads.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        uploads.setPrefHeight(200);
        uploads.setPlaceholder(new Label("No statements uploaded yet"));
    }

    private void chooseStatement() {
        FileChooser fc = new FileChooser();
        fc.setTitle("UPI / bank statement (CSV)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        shell.runBusy("Checking " + f.getName() + "…", () -> ctx.community().uploadStatement(me(), f.toPath()), this::showOutcome);
    }

    private void showOutcome(CommunityService.UploadOutcome o) {
        Upload u = o.upload();
        StatementImporter.Result a = o.analysis();
        ProgressBar meter = new ProgressBar(u.getQuality() / 100.0);
        meter.setMaxWidth(Double.MAX_VALUE);
        String level = u.getStatus() == Upload.Status.ACCEPTED ? "full" : u.getStatus() == Upload.Status.REVIEW ? "half" : "none";
        meter.getStyleClass().addAll("quality-meter", "quality-" + level);
        Label head = Ui.label("Quality " + u.getQuality() + "/100  ·  " + u.getStatus().getLabel(), "section-title");
        Label pill = Ui.label(switch (u.getStatus()) {
            case ACCEPTED -> o.daysAdded() + " day(s) of sales added";
            case REVIEW -> "Held — TrustLend HQ will review it";
            case REJECTED -> "Not counted";
        }, "pill", "evidence-" + level);
        VBox box = new VBox(8, Ui.row(10, head, pill), meter);
        for (String p : a.positives()) box.getChildren().add(Ui.label("✓  " + p, "small", "up"));
        for (String i : u.getIssues().isEmpty() ? List.<String>of() : List.of(u.getIssues().split("\n"))) {
            Label l = Ui.label("⚠  " + i, "small", "down");
            l.setWrapText(true);
            box.getChildren().add(l);
        }
        int delta = o.scoreAfter() - o.scoreBefore();
        box.getChildren().add(Ui.label("Your score: " + o.scoreBefore() + " → " + o.scoreAfter()
                + (delta > 0 ? "  (+" + delta + ")" : ""), "strong"));
        box.getStyleClass().add("upload-result");
        result.getChildren().setAll(box);
        shell.refreshAll();
        if (delta > 0) {
            shell.toast("Statement accepted — score up by " + delta);
            Confetti.burst(shell);
        } else if (u.getStatus() == Upload.Status.REVIEW) {
            shell.toastInfo("Some things looked unusual — the file is waiting for HQ review");
        } else if (u.getStatus() == Upload.Status.REJECTED) {
            shell.toastError("Statement rejected — see the reasons");
        }
    }

    private void saveSample(boolean suspicious) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save a sample statement");
        fc.setInitialFileName(suspicious ? "suspicious_statement.csv" : "phonepe_statement.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try {
            LocalDate today = LocalDate.now();
            List<String> lines = suspicious
                    ? DemoAccounts.statement(today.minusDays(27), 28, System.nanoTime() % 1000, true)
                    : DemoAccounts.statement(today.minusDays(84), 84, System.nanoTime() % 1000, false);
            Files.write(f.toPath(), lines);
            shell.toastInfo("Saved " + f.getName() + " — now upload it with \"Choose statement\"");
        } catch (Exception e) {
            Ui.error("Could not save the sample", e);
        }
    }

    private void declareIncome() {
        try {
            double amount = Validators.parseAmount("monthly income", income.getText());
            int before = ctx.scores().scoreValue(me());
            ctx.community().declareIncome(me(), amount, kind.getValue(), proof == null ? null : proof.toPath());
            int after = ctx.scores().scoreValue(me());
            income.clear();
            proof = null;
            proofLabel.setText("No file attached (optional)");
            shell.refreshAll();
            shell.toast("Income declared" + (after > before ? " — score " + before + " → " + after : "")
                    + ". A lender or HQ can now verify it.");
        } catch (Exception e) {
            Ui.error("Could not declare income", e);
        }
    }

    @Override
    public void refresh() {
        try {
            uploads.getItems().setAll(ctx.community().uploadsOf(me()));
            incomes.getChildren().clear();
            for (IncomeDeclaration d : ctx.community().incomesOf(me())) {
                Label t = Ui.label(Formats.inr(d.getMonthlyAmount()) + " / month · " + d.getKind().getLabel()
                        + " · " + Formats.date(d.getDeclaredOn()), "small");
                HBox.setHgrow(t, Priority.ALWAYS);
                t.setMaxWidth(Double.MAX_VALUE);
                Label s = Ui.label(d.getStatus().getLabel(), "pill", switch (d.getStatus()) {
                    case VERIFIED -> "evidence-full";
                    case DECLARED -> "evidence-half";
                    case REJECTED -> "evidence-none";
                });
                HBox row = new HBox(8, t, s);
                row.setAlignment(Pos.CENTER_LEFT);
                incomes.getChildren().add(row);
            }
            if (incomes.getChildren().isEmpty()) incomes.getChildren().add(Ui.label("None yet", "muted", "small"));
        } catch (Exception e) {
            Ui.error("Could not load uploads", e);
        }
    }
}
