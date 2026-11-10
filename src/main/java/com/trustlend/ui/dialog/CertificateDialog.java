package com.trustlend.ui.dialog;

import com.trustlend.io.PngWriter;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Vendor;
import com.trustlend.service.LoanService;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.ScoreGauge;
import com.trustlend.util.Formats;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * A printable one-page "Community Trust Certificate" for a vendor — what they could show a
 * lender or supplier. It can be saved as a PNG image or printed (choose "Microsoft Print to PDF"
 * or "Save as PDF" in the print dialog to get a PDF).
 */
public class CertificateDialog extends Dialog<Void> {

    /** Everything printed on the certificate, gathered by the caller. */
    public record Data(Vendor vendor, ScoreBreakdown score, LoanService.Offer offer, List<String> vouchedBy,
                       int transactions, int loans, double weightConsistency, double weightRepayment,
                       double weightTenure, double weightNetwork) { }

    private final VBox certificate;
    private final Data data;

    public CertificateDialog(Data data) {
        this.data = data;
        setTitle("Trust certificate — " + data.vendor().getName());
        setHeaderText(null);
        Ui.style(getDialogPane());
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        certificate = build(data);
        StackPane paper = new StackPane(certificate);
        paper.getStyleClass().add("cert-backdrop");
        ScrollPane scroll = new ScrollPane(paper);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(620);
        scroll.setPrefViewportWidth(660);

        Button png = new Button("Save as image (PNG)");
        png.getStyleClass().add("btn-ghost");
        png.setOnAction(e -> savePng());
        Button print = new Button("Print / Save as PDF");
        print.getStyleClass().add("btn-accent");
        print.setOnAction(e -> print());
        Label tip = Ui.label("For a PDF, choose \"Microsoft Print to PDF\" as the printer.", "muted", "small");
        HBox actions = new HBox(10, tip, Ui.hSpacer(), png, print);
        actions.setAlignment(Pos.CENTER_LEFT);
        getDialogPane().setContent(new VBox(12, scroll, actions));
    }

    // ------------------------------------------------------------------ layout

    private static VBox build(Data d) {
        Vendor v = d.vendor();
        ScoreBreakdown s = d.score();
        LocalDate issued = s.computedOn() == null ? LocalDate.now() : s.computedOn();

        // header band
        StackPane mark = new StackPane(Ui.label("T", "cert-mark-text"));
        mark.getStyleClass().add("cert-mark");
        VBox brand = new VBox(0, Ui.label("TrustLend", "cert-brand"), Ui.label("COMMUNITY TRUST CERTIFICATE", "cert-kicker"));
        VBox number = new VBox(0, Ui.label("CERTIFICATE NO.", "cert-kicker"), Ui.label(certificateNo(v, s, issued), "cert-number"));
        number.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(12, mark, brand, Ui.hSpacer(), number);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("cert-header");

        // who
        Label certifies = Ui.label("This certifies the community credit standing of", "cert-small");
        Label name = Ui.label(v.getName(), "cert-name");
        Label role = Ui.label(v.getCategory().getLabel() + "  ·  " + v.getMarketArea() + ", " + v.getCity(), "cert-role");
        Label since = Ui.label(v.getYearsInBusiness() + " years in business  ·  on TrustLend since "
                + Formats.date(v.getJoinDate()), "cert-small");
        VBox who = new VBox(4, certifies, name, role, since);
        who.setAlignment(Pos.CENTER);

        // score + factors
        ScoreGauge gauge = new ScoreGauge(200, 300, 900);
        gauge.setScore(s.score());
        VBox factors = new VBox(10,
                factor("Transaction consistency", s.consistency(), d.weightConsistency(), "consistency"),
                factor("Repayment history", s.repayment(), d.weightRepayment(), "repayment"),
                factor("Business tenure", s.tenure(), d.weightTenure(), "tenure"),
                factor("Community vouches", s.network(), d.weightNetwork(), "network"));
        HBox.setHgrow(factors, Priority.ALWAYS);
        factors.setAlignment(Pos.CENTER_LEFT);
        HBox scoreRow = new HBox(24, gauge, factors);
        scoreRow.setAlignment(Pos.CENTER_LEFT);

        // reasons + vouches + eligibility
        VBox notes = new VBox(5, Ui.label("ASSESSMENT", "cert-section"));
        for (String r : s.reasons().stream().limit(6).toList()) {
            boolean negative = r.contains("defaulted") || r.contains("overdue") || r.contains("No ")
                    || r.contains("swing") || r.contains("capped") || r.contains("provisional");
            Label l = Ui.label((negative ? "▼  " : "▲  ") + r, "cert-reason", negative ? "neg" : "pos");
            l.setWrapText(true);
            notes.getChildren().add(l);
        }
        HBox.setHgrow(notes, Priority.ALWAYS);
        notes.setPrefWidth(300);

        VBox right = new VBox(5, Ui.label("VOUCHED FOR BY", "cert-section"));
        if (d.vouchedBy().isEmpty()) {
            right.getChildren().add(Ui.label("No vouches yet", "cert-small"));
        } else {
            d.vouchedBy().stream().limit(5).forEach(n -> right.getChildren().add(Ui.label("✓  " + n, "cert-text")));
            if (d.vouchedBy().size() > 5) right.getChildren().add(Ui.label("and " + (d.vouchedBy().size() - 5) + " more", "cert-small"));
        }
        LoanService.Offer o = d.offer();
        Label eligibility = Ui.label(o.maxAmount() > 0
                ? "Eligible for micro-credit up to " + Formats.inr(o.maxAmount()) + " at " + String.format("%.1f%%", o.interestRate()) + " p.a."
                : o.note(), "cert-eligibility", o.maxAmount() > 0 ? "ok" : "no");
        eligibility.setWrapText(true);
        right.getChildren().addAll(new javafx.scene.layout.Region(), Ui.label("LOAN ELIGIBILITY", "cert-section"), eligibility);
        right.setPrefWidth(230);
        HBox details = new HBox(24, notes, right);

        // footer
        VBox dates = new VBox(2,
                Ui.label("Issued " + Formats.date(issued) + "  ·  valid until " + Formats.date(issued.plusDays(90)), "cert-text"),
                Ui.label("Based on " + d.transactions() + " transactions, " + d.loans() + " loan(s) and "
                        + d.vouchedBy().size() + " vouch(es)", "cert-small"));
        HBox.setHgrow(dates, Priority.ALWAYS);
        VBox sign = new VBox(2, Ui.label("TrustLend Credit Desk", "cert-signature"), Ui.label("Authorised signatory", "cert-small"));
        sign.setAlignment(Pos.CENTER);
        StackPane seal = new StackPane(Ui.label("VERIFIED\nSCORE " + s.score(), "cert-seal-text"));
        seal.getStyleClass().add("cert-seal");
        HBox footer = new HBox(18, dates, sign, seal);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("cert-footer");

        Label disclaimer = Ui.label("Generated by TrustLend, an alternative credit-scoring project. Scores reflect "
                + "activity recorded in TrustLend and are not a credit bureau report.", "cert-disclaimer");
        disclaimer.setWrapText(true);

        VBox body = new VBox(18, who, scoreRow, details, footer, disclaimer);
        body.setPadding(new javafx.geometry.Insets(0, 28, 0, 28));
        VBox page = new VBox(18, header, body);
        page.getStyleClass().add("certificate");
        page.setPrefWidth(620);
        page.setMaxWidth(620);
        return page;
    }

    private static VBox factor(String name, double value, double weight, String key) {
        ProgressBar bar = new ProgressBar(value);
        bar.getStyleClass().addAll("factor-bar", "factor-" + key);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox head = new HBox(Ui.label(name, "cert-text"), Ui.label("  weight " + Math.round(weight * 100) + "%", "cert-small"),
                Ui.hSpacer(), Ui.label(Math.round(value * 100) + "%", "cert-text", "strong"));
        return new VBox(3, head, bar);
    }

    /** Stable, human-readable id: year, vendor id and a short checksum of the score and date. */
    static String certificateNo(Vendor v, ScoreBreakdown s, LocalDate issued) {
        int check = Math.abs((v.getId() + ":" + s.score() + ":" + issued).hashCode()) % 0xFFFF;
        return String.format("TL-%d-%05d-%04X", issued.getYear(), v.getId(), check);
    }

    // ------------------------------------------------------------------ output

    private void savePng() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save certificate image");
        fc.setInitialFileName("trust-certificate-" + data.vendor().getName().replaceAll("\\W+", "-").toLowerCase() + ".png");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        File file = fc.showSaveDialog(getDialogPane().getScene().getWindow());
        if (file == null) return;
        try {
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(Transform.scale(2, 2));            // 2× for a sharp print-quality image
            params.setFill(Color.WHITE);
            WritableImage img = certificate.snapshot(params, null);
            int w = (int) img.getWidth();
            int h = (int) img.getHeight();
            int[] pixels = new int[w * h];
            img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
            PngWriter.write(file.toPath(), w, h, pixels);
            Ui.info("Certificate saved", "Saved " + file.getName() + " (" + w + " × " + h + " px).");
        } catch (Exception e) {
            Ui.error("Could not save the image", e);
        }
    }

    private void print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Ui.info("No printer", "No printer is installed. Use \"Save as image (PNG)\" instead.");
            return;
        }
        if (!job.showPrintDialog(getDialogPane().getScene().getWindow())) {
            job.cancelJob();
            return;
        }
        PageLayout layout = job.getJobSettings().getPageLayout();
        double w = certificate.getBoundsInParent().getWidth();
        double h = certificate.getBoundsInParent().getHeight();
        double scale = Math.min(1.0, Math.min(layout.getPrintableWidth() / w, layout.getPrintableHeight() / h));
        Scale fit = new Scale(scale, scale);
        certificate.getTransforms().add(fit);
        boolean ok = job.printPage(certificate);
        certificate.getTransforms().remove(fit);
        if (ok) job.endJob();
        else Ui.info("Printing failed", "The printer did not accept the page.");
    }
}
