package com.trustlend.ui.dialog;

import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Vendor;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Avatar;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shown after a loan is marked defaulted: who lost trust, and by how much. The borrower drops
 * hardest; everyone who vouched for them loses credibility; the change ripples further through
 * the network. Returns {@code true} if the user wants to see it on the network graph.
 */
public class RippleDialog extends Dialog<Boolean> {

    public record Row(Vendor vendor, int before, int after, String role) {
        int delta() {
            return after - before;
        }
    }

    /** Builds the rows from the score maps before and after recalculation. */
    public static List<Row> rows(long defaulterId, Set<Long> vouchers, Map<Long, Vendor> vendors,
                                 Map<Long, ScoreBreakdown> before, Map<Long, ScoreBreakdown> after) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(defaulterId);
        ids.addAll(vouchers);
        before.keySet().stream()
                .filter(id -> after.containsKey(id) && Math.abs(after.get(id).score() - before.get(id).score()) >= 3)
                .sorted(Comparator.comparingInt((Long id) -> after.get(id).score() - before.get(id).score()))
                .limit(8)
                .forEach(ids::add);
        String name = vendors.containsKey(defaulterId) ? vendors.get(defaulterId).getName() : "the borrower";
        List<Row> rows = new ArrayList<>();
        for (Long id : ids) {
            if (!before.containsKey(id) || !after.containsKey(id) || !vendors.containsKey(id)) continue;
            String role = id == defaulterId ? "Defaulted" : vouchers.contains(id) ? "Vouched for " + name : "Network effect";
            rows.add(new Row(vendors.get(id), before.get(id).score(), after.get(id).score(), role));
        }
        return rows.stream().limit(9).toList();
    }

    public RippleDialog(String borrower, List<Row> rows) {
        setTitle("Default ripple");
        setHeaderText(null);
        Ui.style(getDialogPane());

        Label title = Ui.label("The ripple effect of " + borrower + "'s default", "dialog-title");
        Label sub = Ui.label("A default doesn't only hurt the borrower. Everyone who vouched for them put their own "
                + "reputation on the line, so their vouching credibility drops — and trust flowing through the "
                + "network shifts for others too.", "muted", "small");
        sub.setWrapText(true);
        VBox list = new VBox(8);
        int maxDrop = rows.stream().mapToInt(r -> Math.abs(r.delta())).max().orElse(1);
        List<Timeline> animations = new ArrayList<>();
        for (Row r : rows) {
            Label name = Ui.label(r.vendor().getName(), "strong");
            Label role = Ui.label(r.role(), "pill", r.role().equals("Defaulted") ? "band-high-risk"
                    : r.role().startsWith("Vouched") ? "band-fair" : "band-good");
            VBox who = new VBox(2, name, role);
            who.setPrefWidth(230);
            Label change = Ui.label(r.before() + "  →  " + r.after(), "ripple-scores");
            change.setMinWidth(Region.USE_PREF_SIZE);
            Label delta = Ui.label((r.delta() > 0 ? "+" : "") + r.delta(), "ripple-delta", r.delta() < 0 ? "down" : "up");
            delta.setMinWidth(48);
            Region bar = new Region();
            bar.getStyleClass().addAll("ripple-bar", r.delta() < 0 ? "down" : "up");
            bar.setPrefWidth(0);
            bar.setMaxWidth(Region.USE_PREF_SIZE);
            StackPane track = new StackPane(bar);
            track.setAlignment(Pos.CENTER_LEFT);
            track.getStyleClass().add("ripple-track");
            track.setPrefWidth(160);
            track.setMinWidth(160);
            HBox row = new HBox(12, new Avatar(r.vendor().getName(), r.vendor().getInitials(), 32), who, change, track, delta);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ripple-row");
            list.getChildren().add(row);
            double width = 160.0 * Math.abs(r.delta()) / Math.max(1, maxDrop);
            animations.add(new Timeline(new KeyFrame(Duration.millis(700), new KeyValue(bar.prefWidthProperty(), width))));
        }
        VBox content = new VBox(14, title, sub, list);
        content.setPrefWidth(680);
        getDialogPane().setContent(content);

        ButtonType network = new ButtonType("Show on the network", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(network, ButtonType.CLOSE);
        getDialogPane().lookupButton(network).getStyleClass().add("btn-accent");
        setResultConverter(b -> b == network);
        setOnShown(e -> animations.forEach(Timeline::play));
    }
}
