package com.trustlend.ui.view;

import com.trustlend.model.User;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.util.Formats;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** TrustLend HQ: every lender with their credibility, and every account in the community. */
public class MembersView extends BaseView {

    private final TableView<CommunityService.LenderStats> lenders = new TableView<>();
    private final TableView<User> accounts = new TableView<>();
    private final Map<Long, Vendor> vendors = new HashMap<>();

    public MembersView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        setHeader("Members", "Lenders are rated by how often borrowers confirm what they report");

        col(lenders, "LENDER", s -> s.lender().getDisplayName());
        col(lenders, "TYPE", s -> s.lender().getLenderType());
        col(lenders, "LOANS", s -> String.valueOf(s.loans()));
        col(lenders, "LENT", s -> Formats.inr(s.lent()));
        col(lenders, "REPORTS", s -> s.reports() + " (" + s.confirmed() + " confirmed, " + s.pending() + " waiting, " + s.disputed() + " disputed)");
        TableColumn<CommunityService.LenderStats, Number> cred = new TableColumn<>("CREDIBILITY");
        cred.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().credibility()));
        cred.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Number n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                ProgressBar bar = new ProgressBar(n.doubleValue());
                bar.setPrefWidth(110);
                bar.getStyleClass().addAll("quality-meter", n.doubleValue() >= 0.6 ? "quality-full" : n.doubleValue() >= 0.4 ? "quality-half" : "quality-none");
                setGraphic(bar);
                setText(Math.round(n.doubleValue() * 100) + "%");
            }
        });
        lenders.getColumns().add(cred);
        lenders.setPrefHeight(220);

        col(accounts, "NAME", u -> u.getFullName());
        col(accounts, "USERNAME", User::getUsername);
        col(accounts, "ROLE", u -> u.getRole().getLabel());
        col(accounts, "DETAILS", u -> u.isLender() ? u.getOrganisation()
                : u.isBorrower() && vendors.containsKey(u.getVendorId())
                ? CommunityService.trustId(u.getVendorId()) + " · " + vendors.get(u.getVendorId()).describeRole()
                + " · score " + vendors.get(u.getVendorId()).getCurrentScore() : "Oversees the community");
        accounts.setPrefHeight(360);

        Label note = Ui.label("Credibility = (confirmed + 1) / (confirmed + disputed + 2). A new lender starts at 50%. "
                + "Every report a borrower confirms raises it; every report they dispute (and HQ rejects) lowers it.", "muted", "small");
        note.setWrapText(true);
        setCenter(new VBox(16, Ui.titledCard("Lenders", lenders, note), Ui.titledCard("All accounts", accounts)));
    }

    private static <T> void col(TableView<T> table, String title, java.util.function.Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        table.getColumns().add(c);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    @Override
    public void refresh() {
        try {
            vendors.clear();
            ctx.vendors().findAll().forEach(v -> vendors.put(v.getId(), v));
            List<CommunityService.LenderStats> stats = new ArrayList<>();
            for (User l : ctx.community().lenders()) stats.add(ctx.community().lenderStats(l));
            lenders.getItems().setAll(stats);
            accounts.getItems().setAll(ctx.community().members());
        } catch (Exception e) {
            Ui.error("Could not load members", e);
        }
    }
}
