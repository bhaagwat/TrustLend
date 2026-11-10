package com.trustlend.ui.component;

import com.trustlend.ui.MainWindow;
import com.trustlend.ui.MainWindow.Page;
import com.trustlend.ui.Ui;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * A step-by-step walkthrough shown as a floating card in the bottom-right corner.
 * Each step opens a screen, highlights it in the sidebar and suggests something to try.
 * The rest of the window stays usable, so the viewer can follow the "Try this" hints live.
 */
public class GuidedTour extends VBox {

    /** One stop of the tour. {@code tryThis} may be null. */
    public record Step(Page page, String title, String body, String tryThis) { }

    public static final List<Step> STEPS = List.of(
            new Step(Page.DASHBOARD, "Welcome to TrustLend",
                    "Street vendors rarely have credit histories, so banks turn them away. TrustLend gives each "
                            + "vendor a CIBIL-style trust score from 300 to 900, built from how they actually run "
                            + "their business. The dashboard summarises the whole portfolio.",
                    "For the 2-minute version, press 'Story mode: Meet Sunil' in the sidebar - an animated story of one chai seller earning his first fair loan."),
            new Step(Page.VENDORS, "Vendors",
                    "Every vendor in your workspace with their live score and risk band. Search by name, phone, "
                            + "market or city, and sort any column.",
                    "Click '+ Add vendor' and create one. A new vendor starts with a low score because there is no history yet."),
            new Step(Page.VENDORS, "Inside a vendor's profile",
                    "Double-click any vendor. The profile shows the score gauge, the four factors that make up "
                            + "the score (consistency, repayment, tenure, community vouches), a plain-English "
                            + "'Why this score', the score history, and a 'What if…' simulator.",
                    "Open a low-scoring vendor and drag the 'What if…' sliders - the full engine re-runs and shows the projected score and loan offer. Then press 'Trust certificate'."),
            new Step(Page.NETWORK, "The trust network",
                    "Vouches form a weighted, directed graph. Trust flows along the arrows using an "
                            + "EigenTrust / PageRank-style algorithm, so a vouch from a highly trusted vendor counts "
                            + "more than one from a newcomer. Vouching for someone who defaults costs you credibility.",
                    "Pick two vendors under 'Find strongest trust path' to see how trust travels between them."),
            new Step(Page.LOANS, "Loans priced by trust",
                    "A better score means a bigger limit and a lower interest rate. Below the minimum score a "
                            + "loan is refused. Repayments are tracked instalment by instalment, and overdue loans are "
                            + "detected automatically.",
                    "Press '₹ New loan' for an instant decision card with the repayment schedule. Then 'Mark defaulted' on a loan to see the ripple effect on everyone who vouched."),
            new Step(Page.TRANSACTIONS, "Daily transactions",
                    "Sales, stock purchases and savings deposits. Regular activity week after week is the "
                            + "'transaction consistency' factor, the raw signal behind the score.",
                    "Record a transaction for a vendor, then go back to the Dashboard and press 'Recalculate scores'."),
            new Step(Page.DATA, "Reports & data",
                    "Export vendors and transactions to CSV, write a portfolio report, bulk-import a ledger "
                            + "from a CSV file, and back up or restore the whole workspace. The activity log shows "
                            + "what the app did.",
                    "Press 'Portfolio report (.txt)' to create a printable summary."),
            new Step(Page.SETTINGS, "Tune the model",
                    "Change how much each factor counts, how strongly trust flows through the network, and the "
                            + "lending policy. Saving recalculates every score instantly. You can also load or clear "
                            + "demo data here.",
                    "Drag 'Community vouches (graph)' to the far right and press 'Save & recalculate' - well-connected vendors climb the ranking."),
            new Step(Page.DASHBOARD, "That's the tour!",
                    "Everything you do is saved in your own workspace, separate from other accounts on this "
                            + "computer. The moon and frame buttons in the sidebar switch on dark mode and a "
                            + "large-text presentation mode for projectors.",
                    null));

    private final MainWindow shell;
    private final Runnable onClose;
    private final Label counter = Ui.label("", "tour-counter");
    private final Label title = Ui.label("", "tour-title");
    private final Label body = Ui.label("", "tour-body");
    private final Label tryLabel = Ui.label("", "tour-try");
    private final Button back = new Button("Back");
    private final Button next = new Button("Next");
    private int index;

    public GuidedTour(MainWindow shell, Runnable onClose) {
        this.shell = shell;
        this.onClose = onClose;
        getStyleClass().add("tour-card");
        setSpacing(10);
        setMaxSize(380, USE_PREF_SIZE);
        setPrefWidth(380);
        StackPane.setAlignment(this, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(this, new Insets(0, 24, 24, 0));

        body.setWrapText(true);
        tryLabel.setWrapText(true);
        tryLabel.setMaxWidth(Double.MAX_VALUE);
        title.setWrapText(true);

        Button close = new Button("✕");
        close.getStyleClass().add("tour-close");
        close.setOnAction(e -> close());
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(e -> show(index - 1));
        next.getStyleClass().add("btn-accent");
        next.setOnAction(e -> {
            if (index == STEPS.size() - 1) close();
            else show(index + 1);
        });
        HBox top = Ui.row(8, counter, Ui.hSpacer(), close);
        HBox buttons = Ui.row(8, Ui.hSpacer(), back, next);
        getChildren().addAll(top, title, body, tryLabel, buttons);
    }

    /** Shows the first step with a short slide-in animation. */
    public void start() {
        show(0);
        setOpacity(0);
        setTranslateY(30);
        FadeTransition fade = new FadeTransition(Duration.millis(250), this);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(250), this);
        slide.setToY(0);
        fade.play();
        slide.play();
    }

    private void show(int i) {
        index = Math.max(0, Math.min(STEPS.size() - 1, i));
        Step s = STEPS.get(index);
        shell.navigate(s.page());
        shell.highlightNav(s.page());
        counter.setText("GUIDED TOUR · " + (index + 1) + " / " + STEPS.size());
        title.setText(s.title());
        body.setText(s.body());
        tryLabel.setText(s.tryThis() == null ? "" : "Try this:  " + s.tryThis());
        tryLabel.setVisible(s.tryThis() != null);
        tryLabel.setManaged(s.tryThis() != null);
        back.setDisable(index == 0);
        next.setText(index == STEPS.size() - 1 ? "Finish" : "Next  →");
        next.requestFocus();
    }

    private void close() {
        shell.highlightNav(null);
        onClose.run();
    }
}
