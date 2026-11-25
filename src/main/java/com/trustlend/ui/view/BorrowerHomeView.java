package com.trustlend.ui.view;

import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.User;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.service.LoanService;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.PhoneAccess;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.EvidencePanel;
import com.trustlend.ui.component.FactorBar;
import com.trustlend.ui.component.Icons;
import com.trustlend.ui.component.ScoreGauge;
import com.trustlend.ui.component.WhatIfPanel;
import com.trustlend.ui.dialog.CertificateDialog;
import com.trustlend.util.Formats;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A borrower's own home page: their score, what it is built from, what is waiting for them, and
 * concrete things they can do to raise it. Borrowers never see anyone else's data.
 */
public class BorrowerHomeView extends BaseView {

    private final long vendorId;
    private final ScoreGauge gauge;
    private final Label band = Ui.label("", "pill");
    private final Label offer = Ui.label("", "offer-ok");
    private final FactorBar consistency;
    private final FactorBar repayment;
    private final FactorBar tenure;
    private final FactorBar network;
    private final VBox alert = new VBox(10);
    private final VBox tips = new VBox(10);
    private final EvidencePanel evidence;
    private final WhatIfPanel whatIf;

    public BorrowerHomeView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        User me = shell.user();
        this.vendorId = me.getVendorId();
        var w = ctx.scores().getSettings();
        gauge = new ScoreGauge(230, w.minScore(), w.maxScore());
        consistency = new FactorBar("Regular sales", "consistency", w.wConsistency(), "Selling steadily week after week");
        repayment = new FactorBar("Repaying on time", "repayment", w.wRepayment(), "Instalments paid on time, confirmed by you and your lender");
        tenure = new FactorBar("Tenure & income", "tenure", w.wTenure(), "Years in business, time on TrustLend, your income");
        network = new FactorBar("Community trust", "network", w.wNetwork(), "Vouches from trusted members");

        Button certificate = new Button("My trust certificate", Icons.of("award", 16));
        certificate.getStyleClass().add("btn-ghost");
        certificate.setOnAction(e -> CertificateDialog.open(ctx, vendorId));
        Button phone = new Button("Use on my phone", Icons.of("phone", 16));
        phone.getStyleClass().add("btn-ghost");
        phone.setOnAction(e -> PhoneAccess.show(ctx, shell));
        setHeader("Namaste, " + me.getFullName().split("\\s+")[0],
                "Your TrustLend ID is " + CommunityService.trustId(vendorId)
                        + " — give it to lenders so they can report your repayments.", phone, certificate);

        offer.setWrapText(true);
        offer.setMaxWidth(250);
        band.setMinWidth(Region.USE_PREF_SIZE);
        VBox scoreCard = Ui.card(Ui.label("MY TRUST SCORE", "form-label"), gauge, band, offer);
        scoreCard.setAlignment(Pos.TOP_CENTER);
        scoreCard.setMinWidth(290);
        scoreCard.setMaxWidth(290);

        VBox factors = Ui.titledCard("What makes up my score", consistency, repayment, tenure, network);
        HBox.setHgrow(factors, Priority.ALWAYS);
        factors.setPrefWidth(360);

        VBox tipsCard = Ui.titledCard("How to raise my score", tips);
        HBox.setHgrow(tipsCard, Priority.ALWAYS);
        tipsCard.setPrefWidth(380);

        alert.getStyleClass().add("borrower-alert");
        evidence = new EvidencePanel(ctx, vendorId, me, this::refresh);
        whatIf = new WhatIfPanel(ctx, vendorId);

        VBox content = new VBox(16, alert, new HBox(16, scoreCard, factors, tipsCard), evidence, whatIf);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    @Override
    public void refresh() {
        try {
            Vendor me = ctx.vendors().get(vendorId);
            ScoreBreakdown s = ctx.scores().scoreOf(vendorId).orElse(null);
            if (s != null) {
                gauge.setScore(s.score());
                consistency.setValue(s.consistency());
                repayment.setValue(s.repayment());
                tenure.setValue(s.tenure());
                network.setValue(s.network());
                band.setText(s.band().getLabel());
                band.getStyleClass().setAll("label", "pill", s.band().styleClass());
            }
            LoanService.Offer o = ctx.loans().offerFor(vendorId);
            offer.setText(o.maxAmount() > 0
                    ? "You can borrow up to " + Formats.inr(o.maxAmount()) + " at " + String.format("%.1f%%", o.interestRate()) + " a year"
                    : o.note());
            offer.getStyleClass().setAll("label", o.maxAmount() > 0 ? "offer-ok" : "offer-no");

            CommunityService.Evidence e = ctx.community().evidence(vendorId);
            int pending = ctx.community().pendingClaims(vendorId).size();
            alert.getChildren().clear();
            alert.setVisible(pending > 0);
            alert.setManaged(pending > 0);
            if (pending > 0) {
                Button review = new Button("Review now");
                review.getStyleClass().add("btn-accent");
                review.setOnAction(ev -> shell.navigate(MainWindow.Page.CONFIRM));
                Label text = Ui.label(pending + " repayment(s) reported by your lenders are waiting for you. "
                        + "Confirm the ones that are true — they then count fully towards your score.", "strong");
                text.setWrapText(true);
                HBox.setHgrow(text, Priority.ALWAYS);
                HBox row = new HBox(14, Icons.of("help", 26), text, review);
                row.setAlignment(Pos.CENTER_LEFT);
                alert.getChildren().add(row);
            }

            tips.getChildren().clear();
            if (pending > 0) tip("Confirm the repayments your lenders reported", "Review", MainWindow.Page.CONFIRM);
            if (e.uploadedSaleDays() < 30) tip("Upload your PhonePe / Google Pay / bank statement — every day of sales you can prove helps", "Upload", MainWindow.Page.UPLOAD);
            if (e.verifiedIncome() == 0 && e.declaredIncome() == 0) tip("Declare your monthly income (salary slip or business income)", "Declare", MainWindow.Page.UPLOAD);
            if (e.declaredIncome() > 0 && e.verifiedIncome() == 0) tip("Ask a lender to verify your income — then it counts fully", null, null);
            if (e.vouches() < 2) tip("Ask a trusted neighbour who uses TrustLend to vouch for you", null, null);
            if (e.reportedLoans() == 0) tip("Have you repaid a moneylender, SHG or wholesaler before? Ask them to report it on TrustLend", null, null);
            tip("Pay every instalment on time — late payments pull the score down", null, null);
            if (me.getYearsInBusiness() < 2) tip("Keep going: your score grows as you stay longer in business", null, null);

            evidence.refresh();
            whatIf.refresh();
        } catch (Exception ex) {
            Ui.error("Could not load your score", ex);
        }
    }

    private void tip(String text, String action, MainWindow.Page page) {
        Label t = Ui.label(text, "small");
        t.setWrapText(true);
        HBox.setHgrow(t, Priority.ALWAYS);
        HBox row = new HBox(10, Icons.of("spark", 16), t);
        row.setAlignment(Pos.CENTER_LEFT);
        if (action != null) {
            Button b = new Button(action);
            b.getStyleClass().add("btn-ghost");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(ev -> shell.navigate(page));
            row.getChildren().add(b);
        }
        row.getStyleClass().add("tip-row");
        tips.getChildren().add(row);
    }
}
