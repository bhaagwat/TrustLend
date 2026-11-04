package com.trustlend.ui.dialog;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Vendor;
import com.trustlend.model.Vouch;
import com.trustlend.service.AppContext;
import com.trustlend.service.VouchService;
import com.trustlend.ui.Ui;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;

import java.util.Comparator;
import java.util.List;

/** Create a vouch, with a "what-if" preview of how it would change the vouchee's score. */
public class VouchDialog extends FormDialog<Vouch> {

    private static final String[] STRENGTH_TEXT = {
            "", "Know them a little", "Know them well", "Trust them", "Trust them strongly", "Would stake my own credit"
    };

    private final AppContext ctx;
    private final ComboBox<Vendor> voucher;
    private final ComboBox<Vendor> vouchee;
    private final Slider strength = new Slider(1, 5, 3);
    private final Label strengthLabel = Ui.label("", "muted", "small");
    private final ComboBox<Vouch.Relationship> relationship =
            new ComboBox<>(FXCollections.observableArrayList(Vouch.Relationship.values()));
    private final Label impact = Ui.label("", "small");

    public VouchDialog(AppContext ctx, List<Vendor> vendors, Vendor context) {
        super("Add vouch", "Record that one vendor vouches for another", "Add vouch");
        this.ctx = ctx;
        List<Vendor> sorted = vendors.stream().filter(Vendor::isActive)
                .sorted(Comparator.comparing(Vendor::getName)).toList();
        voucher = new ComboBox<>(FXCollections.observableArrayList(sorted));
        vouchee = new ComboBox<>(FXCollections.observableArrayList(sorted));
        voucher.setPromptText("Who is vouching?");
        vouchee.setPromptText("Who are they vouching for?");
        if (context != null) vouchee.setValue(context);

        strength.setMajorTickUnit(1);
        strength.setMinorTickCount(0);
        strength.setSnapToTicks(true);
        strength.setShowTickMarks(true);
        strength.setShowTickLabels(true);
        strength.valueProperty().addListener((o, a, b) -> strengthLabel.setText(STRENGTH_TEXT[(int) Math.round(b.doubleValue())]));
        strengthLabel.setText(STRENGTH_TEXT[3]);
        relationship.setValue(Vouch.Relationship.NEIGHBOURING_STALL);

        Button simulate = new Button("Preview impact");
        simulate.getStyleClass().add("btn-ghost");
        simulate.setOnAction(e -> preview());
        impact.setWrapText(true);
        impact.setMaxWidth(300);

        addRow("Voucher", "voucher", voucher);
        addRow("Vouches for", "vouchee", vouchee);
        addRow("Strength", "strength", strength);
        addRow("", null, strengthLabel);
        addRow("Relationship", "relationship", relationship);
        addFullRow(new HBox(12, simulate, impact));
        Label rules = Ui.label("Rules: no self-vouching · voucher needs a score of at least "
                + VouchService.MIN_VOUCHER_SCORE + " · max " + VouchService.MAX_VOUCHES_GIVEN
                + " vouches per vendor · if the vouchee defaults, the voucher's credibility drops.", "muted", "small");
        rules.setWrapText(true);
        rules.setMaxWidth(440);
        addFullRow(rules);
    }

    private void preview() {
        Vendor from = voucher.getValue();
        Vendor to = vouchee.getValue();
        if (from == null || to == null || from.equals(to)) {
            impact.setText("Choose two different vendors first");
            return;
        }
        try {
            int before = ctx.scores().scoreValue(to.getId());
            ScoreBreakdown after = ctx.scores().simulateVouch(from.getId(), to.getId(), (int) strength.getValue());
            int delta = after.score() - before;
            impact.setText(to.getName() + ": " + before + " → " + after.score()
                    + (delta >= 0 ? "  (+" + delta + ")" : "  (" + delta + ")"));
            impact.setStyle("-fx-text-fill: " + (delta >= 0 ? "#0b7a43" : "#b3261e") + "; -fx-font-weight: bold;");
        } catch (Exception ex) {
            impact.setText(ex.getMessage());
        }
    }

    @Override
    protected Vouch submit() throws Exception {
        if (voucher.getValue() == null) throw new ValidationException("voucher", "Choose who is vouching");
        if (vouchee.getValue() == null) throw new ValidationException("vouchee", "Choose who they vouch for");
        try {
            return ctx.vouches().addVouch(voucher.getValue().getId(), vouchee.getValue().getId(),
                    (int) Math.round(strength.getValue()), relationship.getValue());
        } catch (com.trustlend.exception.InvalidVouchException e) {
            throw new ValidationException("voucher", e.getMessage());
        }
    }
}
