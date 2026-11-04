package com.trustlend.ui.view;

import com.trustlend.config.AppConfig;
import com.trustlend.io.BackupService;
import com.trustlend.io.BackupSnapshot;
import com.trustlend.io.CsvExporter;
import com.trustlend.io.CsvTransactionImporter;
import com.trustlend.io.ReportWriter;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** File handling screen: CSV export/import, text reports, serialized backups and the log viewer. */
public class DataView extends BaseView {

    private final TextArea logArea = new TextArea();
    private final CheckBox warningsOnly = new CheckBox("Warnings & errors only");

    public DataView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        setHeader("Reports & Data", "Export, import, back up and audit — everything stays on this computer");

        VBox export = Ui.titledCard("Export",
                desc("Spreadsheet-ready CSV files (UTF-8) and a formatted plain-text portfolio report."),
                action("Vendors & trust scores (.csv)", "btn-ghost", this::exportVendors),
                action("All transactions (.csv)", "btn-ghost", this::exportTransactions),
                action("Portfolio report (.txt)", "btn-primary", this::writeReport));

        VBox importCard = Ui.titledCard("Import transactions",
                desc("Bulk-load a ledger kept in Excel or exported from a UPI app. Columns: "
                        + "phone, date (yyyy-MM-dd), type (SALE / PURCHASE / DEPOSIT), amount, note."),
                action("Import from CSV…", "btn-primary", this::importCsv),
                action("Save a CSV template", "btn-ghost", this::saveTemplate));

        VBox backup = Ui.titledCard("Backup & restore",
                desc("Backups are Java-serialized snapshots of every vendor, loan, repayment, vouch and transaction."),
                action("Create backup", "btn-primary", this::createBackup),
                action("Restore from backup…", "btn-danger", this::restoreBackup),
                action("Copy raw database file", "btn-ghost", this::copyDatabase));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.add(export, 0, 0);
        grid.add(importCard, 1, 0);
        grid.add(backup, 2, 0);
        for (VBox c : List.of(export, importCard, backup)) {
            GridPane.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
            c.setPrefWidth(300);
        }

        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'Consolas', 'Menlo', monospace; -fx-font-size: 11.5px;");
        logArea.setPrefRowCount(16);
        Button reload = new Button("Reload");
        reload.getStyleClass().add("btn-ghost");
        reload.setOnAction(e -> loadLog());
        warningsOnly.selectedProperty().addListener((o, a, b) -> loadLog());
        VBox logCard = Ui.titledCard("Activity log  (java.util.logging → data/logs)",
                Ui.row(10, warningsOnly, Ui.hSpacer(), reload), logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox content = new VBox(16, grid, logCard);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private static Label desc(String text) {
        Label l = Ui.label(text, "muted", "small");
        l.setWrapText(true);
        return l;
    }

    private static Button action(String text, String style, Runnable r) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> r.run());
        return b;
    }

    @Override
    public void refresh() {
        loadLog();
    }

    // ------------------------------------------------------------------ helpers

    private Path exportDir() {
        Path dir = AppConfig.getInstance().getPath("export.dir");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // FileChooser will fall back to the user's home folder
        }
        return dir;
    }

    private Path chooseSave(String title, String name, String description, String pattern, Path dir) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialFileName(name);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
        File d = dir.toFile();
        if (d.isDirectory()) fc.setInitialDirectory(d.getAbsoluteFile());
        File f = fc.showSaveDialog(getScene().getWindow());
        return f == null ? null : f.toPath();
    }

    private Path chooseOpen(String title, String description, String pattern, Path dir) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
        File d = dir.toFile();
        if (d.isDirectory()) fc.setInitialDirectory(d.getAbsoluteFile());
        File f = fc.showOpenDialog(getScene().getWindow());
        return f == null ? null : f.toPath();
    }

    // ------------------------------------------------------------------ export

    private void exportVendors() {
        Path file = chooseSave("Export vendors", "vendors-" + LocalDate.now() + ".csv", "CSV files", "*.csv", exportDir());
        if (file == null) return;
        try {
            Map<Long, ScoreBreakdown> scores = ctx.scores().latest();
            List<Vendor> vendors = ctx.vendors().ranked();
            int n = new CsvExporter<Vendor>()
                    .column("ID", Vendor::getId)
                    .column("Name", Vendor::getName)
                    .column("Phone", Vendor::getPhone)
                    .column("Business", v -> v.getCategory().getLabel())
                    .column("Market", Vendor::getMarketArea)
                    .column("City", Vendor::getCity)
                    .column("Years in business", Vendor::getYearsInBusiness)
                    .column("Joined", Vendor::getJoinDate)
                    .column("Active", Vendor::isActive)
                    .column("Trust score", Vendor::getCurrentScore)
                    .column("Band", v -> v.getRiskBand().getLabel())
                    .column("Consistency", v -> pct(scores.get(v.getId()), ScoreBreakdown::consistency))
                    .column("Repayment", v -> pct(scores.get(v.getId()), ScoreBreakdown::repayment))
                    .column("Tenure", v -> pct(scores.get(v.getId()), ScoreBreakdown::tenure))
                    .column("Network", v -> pct(scores.get(v.getId()), ScoreBreakdown::network))
                    .write(file, vendors);
            shell.toast("Exported " + n + " vendors to " + file.getFileName());
        } catch (Exception e) {
            Ui.error("Export failed", e);
        }
    }

    private static String pct(ScoreBreakdown s, java.util.function.ToDoubleFunction<ScoreBreakdown> f) {
        return s == null ? "" : String.format("%.2f", f.applyAsDouble(s));
    }

    private void exportTransactions() {
        Path file = chooseSave("Export transactions", "transactions-" + LocalDate.now() + ".csv", "CSV files", "*.csv", exportDir());
        if (file == null) return;
        try {
            Map<Long, Vendor> byId = new HashMap<>();
            ctx.vendors().findAll().forEach(v -> byId.put(v.getId(), v));
            int n = new CsvExporter<Transaction>()
                    .column("ID", Transaction::getId)
                    .column("Date", Transaction::getDate)
                    .column("Vendor", t -> byId.containsKey(t.getVendorId()) ? byId.get(t.getVendorId()).getName() : "")
                    .column("Phone", t -> byId.containsKey(t.getVendorId()) ? byId.get(t.getVendorId()).getPhone() : "")
                    .column("Type", Transaction::getType)
                    .column("Amount", t -> String.format("%.2f", t.getAmount()))
                    .column("Note", Transaction::getNote)
                    .write(file, ctx.transactions().all());
            shell.toast("Exported " + n + " transactions");
        } catch (Exception e) {
            Ui.error("Export failed", e);
        }
    }

    private void writeReport() {
        Path file = chooseSave("Portfolio report", "trustlend-report-" + LocalDate.now() + ".txt", "Text files", "*.txt", exportDir());
        if (file == null) return;
        try {
            new ReportWriter().writePortfolioReport(file, ctx.analytics().compute(), ctx.vendors().ranked(),
                    ctx.loans().listLoans(), ctx.scores().latest());
            shell.toast("Report saved: " + file.getFileName());
        } catch (Exception e) {
            Ui.error("Could not write report", e);
        }
    }

    // ------------------------------------------------------------------ import

    private void saveTemplate() {
        Path file = chooseSave("Save template", "transactions-template.csv", "CSV files", "*.csv", exportDir());
        if (file == null) return;
        try {
            String phone = ctx.vendors().findAll().stream().findFirst().map(Vendor::getPhone).orElse("9876543210");
            CsvTransactionImporter.writeTemplate(file, phone);
            shell.toast("Template saved — fill it in and import it");
        } catch (Exception e) {
            Ui.error("Could not save template", e);
        }
    }

    private void importCsv() {
        Path file = chooseOpen("Import transactions", "CSV files", "*.csv", exportDir());
        if (file == null) return;
        try {
            CsvTransactionImporter.Result r = ctx.importer().parse(file);
            String message = r.transactions().size() + " valid transaction(s) found in " + r.linesRead() + " line(s).";
            if (!r.errors().isEmpty()) {
                message += "\n\n" + r.errors().size() + " line(s) will be skipped:\n"
                        + r.errors().stream().limit(8).collect(Collectors.joining("\n"))
                        + (r.errors().size() > 8 ? "\n…" : "");
            }
            if (r.transactions().isEmpty()) {
                Ui.info("Nothing to import", message);
                return;
            }
            if (Ui.confirm("Import transactions", message + "\n\nImport now?", "Import")) {
                int n = ctx.transactions().importBatch(new ArrayList<>(r.transactions()));
                shell.recalculateScores(() -> "Imported " + n + " transactions; scores updated");
            }
        } catch (Exception e) {
            Ui.error("Import failed", e);
        }
    }

    // ------------------------------------------------------------------ backup

    private void createBackup() {
        Path suggested = BackupService.defaultBackupFile();
        try {
            Files.createDirectories(suggested.getParent());
        } catch (IOException ignored) {
            // handled by the chooser
        }
        Path file = chooseSave("Create backup", suggested.getFileName().toString(), "TrustLend backups", "*.tlbak",
                suggested.getParent());
        if (file == null) return;
        try {
            BackupSnapshot s = ctx.backup().exportSnapshot(file);
            shell.toast("Backup saved (" + s.summary() + ")");
        } catch (Exception e) {
            Ui.error("Backup failed", e);
        }
    }

    private void restoreBackup() {
        Path file = chooseOpen("Restore backup", "TrustLend backups", "*.tlbak",
                AppConfig.getInstance().getPath("backup.dir"));
        if (file == null) return;
        try {
            BackupSnapshot preview = ctx.backup().readSnapshot(file);
            if (!Ui.confirm("Restore backup", "This backup was created on "
                    + Formats.date(preview.getCreatedAt().toLocalDate()) + " and contains " + preview.summary()
                    + ".\n\nALL current data will be replaced. Continue?", "Restore")) {
                return;
            }
            ctx.backup().importSnapshot(file);
            shell.recalculateScores(() -> "Backup restored: " + preview.summary());
        } catch (Exception e) {
            Ui.error("Restore failed", e);
        }
    }

    private void copyDatabase() {
        Path db = AppConfig.getInstance().getPath("db.path");
        Path file = chooseSave("Copy database file", "trustlend-copy-" + LocalDate.now() + ".db", "SQLite database",
                "*.db", exportDir());
        if (file == null) return;
        try {
            ctx.db().execute(c -> {
                try (var st = c.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(FULL)");   // flush WAL so the file is complete
                }
                return null;
            });
            long bytes = BackupService.copyFile(db, file);
            shell.toast("Copied " + (bytes / 1024) + " KB to " + file.getFileName());
        } catch (Exception e) {
            Ui.error("Copy failed", e);
        }
    }

    // ------------------------------------------------------------------ logs

    private void loadLog() {
        Path log = Paths.get("data", "logs", "trustlend-0.log");
        if (!Files.exists(log)) {
            logArea.setText("No log file yet.");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            List<String> shown = new ArrayList<>();
            for (String line : lines) {
                if (!warningsOnly.isSelected() || line.contains("[WARNING]") || line.contains("[SEVERE"))
                    shown.add(line);
            }
            int from = Math.max(0, shown.size() - 300);
            logArea.setText(String.join("\n", shown.subList(from, shown.size())));
            logArea.positionCaret(logArea.getLength());
        } catch (IOException e) {
            logArea.setText("Could not read log: " + e.getMessage());
        }
    }
}
