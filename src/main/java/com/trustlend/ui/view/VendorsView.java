package com.trustlend.ui.view;

import com.trustlend.model.RiskBand;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Avatar;
import com.trustlend.ui.dialog.VendorDialog;
import com.trustlend.util.Formats;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** Searchable, filterable vendor register backed by FilteredList → SortedList → TableView. */
public class VendorsView extends BaseView {

    private final ObservableList<Vendor> master = FXCollections.observableArrayList();
    private final FilteredList<Vendor> filtered = new FilteredList<>(master, v -> true);
    private final TableView<Vendor> table = new TableView<>();
    private final TextField search = new TextField();
    private final ComboBox<Object> bandFilter = new ComboBox<>();
    private final ComboBox<Object> categoryFilter = new ComboBox<>();
    private final Label countLabel = Ui.label("", "muted");

    public VendorsView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);

        Button add = new Button("+  Add vendor");
        add.getStyleClass().add("btn-primary");
        add.setOnAction(e -> addVendor());
        setHeader("Vendors", "Everyone in the TrustLend community, with their live trust score", add);

        search.setPromptText("Search name, phone, market…");
        search.getStyleClass().add("search-field");
        bandFilter.getItems().add("All bands");
        bandFilter.getItems().addAll((Object[]) RiskBand.values());
        bandFilter.getSelectionModel().selectFirst();
        categoryFilter.getItems().add("All businesses");
        categoryFilter.getItems().addAll((Object[]) VendorCategory.values());
        categoryFilter.getSelectionModel().selectFirst();

        // Any change in a filter control re-computes the FilteredList predicate (listeners)
        search.textProperty().addListener((o, a, b) -> applyFilter());
        bandFilter.valueProperty().addListener((o, a, b) -> applyFilter());
        categoryFilter.valueProperty().addListener((o, a, b) -> applyFilter());

        HBox filters = Ui.row(10, search, bandFilter, categoryFilter, Ui.hSpacer(), countLabel);

        buildTable();
        SortedList<Vendor> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        Button open = new Button("Open profile");
        open.getStyleClass().add("btn-ghost");
        Button edit = new Button("Edit");
        edit.getStyleClass().add("btn-ghost");
        Button toggle = new Button("Deactivate");
        toggle.getStyleClass().add("btn-ghost");
        Button delete = new Button("Delete");
        delete.getStyleClass().add("btn-danger");
        open.setOnAction(e -> selected().ifPresent(v -> shell.openVendor(v.getId())));
        edit.setOnAction(e -> selected().ifPresent(this::editVendor));
        toggle.setOnAction(e -> selected().ifPresent(this::toggleActive));
        delete.setOnAction(e -> selected().ifPresent(this::deleteVendor));
        for (Button b : new Button[]{open, edit, toggle, delete}) {
            b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        }
        table.getSelectionModel().selectedItemProperty().addListener((o, a, v) ->
                toggle.setText(v != null && !v.isActive() ? "Activate" : "Deactivate"));
        HBox actions = Ui.row(8, open, edit, toggle, Ui.hSpacer(), delete);

        VBox body = new VBox(14, filters, table, actions);
        javafx.scene.layout.VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        setCenter(body);
    }

    private java.util.Optional<Vendor> selected() {
        return java.util.Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    @SuppressWarnings("unchecked")
    private void buildTable() {
        TableColumn<Vendor, Vendor> nameCol = new TableColumn<>("VENDOR");
        nameCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        nameCol.setComparator(Comparator.comparing(Vendor::getName, String.CASE_INSENSITIVE_ORDER));
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Vendor v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setGraphic(null);
                    return;
                }
                VBox text = new VBox(0, Ui.label(v.getName(), "strong"), Ui.label("+91 " + v.getPhone(), "muted", "small"));
                setGraphic(Ui.row(10, new Avatar(v.getName(), v.getInitials(), 30), text));
                setText(null);
            }
        });
        nameCol.setPrefWidth(250);

        TableColumn<Vendor, String> catCol = new TableColumn<>("BUSINESS");
        catCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getCategory().getLabel()));
        catCol.setPrefWidth(170);

        TableColumn<Vendor, String> marketCol = new TableColumn<>("MARKET");
        marketCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getMarketArea()));
        marketCol.setPrefWidth(150);

        TableColumn<Vendor, Number> scoreCol = new TableColumn<>("TRUST SCORE");
        scoreCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getCurrentScore()));
        scoreCol.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            private final Label value = Ui.label("", "strong");
            {
                bar.setPrefWidth(90);
                bar.setPrefHeight(8);
            }

            @Override
            protected void updateItem(Number score, boolean empty) {
                super.updateItem(score, empty);
                if (empty || score == null) {
                    setGraphic(null);
                    return;
                }
                value.setText(String.valueOf(score.intValue()));
                bar.setProgress((score.intValue() - 300) / 600.0);
                bar.setStyle("-fx-accent: " + RiskBand.fromScore(score.intValue()).getColorHex() + ";");
                setGraphic(Ui.row(10, value, bar));
            }
        });
        scoreCol.setPrefWidth(170);
        scoreCol.setSortType(TableColumn.SortType.DESCENDING);

        TableColumn<Vendor, RiskBand> bandCol = new TableColumn<>("BAND");
        bandCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRiskBand()));
        bandCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(RiskBand band, boolean empty) {
                super.updateItem(band, empty);
                setGraphic(empty || band == null ? null : Ui.bandPill(band));
            }
        });
        bandCol.setPrefWidth(110);

        TableColumn<Vendor, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().isActive() ? "Active" : "Inactive"));
        statusCol.setPrefWidth(90);

        TableColumn<Vendor, LocalDate> joinCol = new TableColumn<>("JOINED");
        joinCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getJoinDate()));
        joinCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : Formats.date(d));
            }
        });
        joinCol.setPrefWidth(120);

        table.getColumns().addAll(nameCol, catCol, marketCol, scoreCol, bandCol, statusCol, joinCol);
        table.getSortOrder().add(scoreCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No vendors match your filters"));

        // Double-click opens the profile; right-click shows a context menu
        table.setRowFactory(tv -> {
            TableRow<Vendor> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    shell.openVendor(row.getItem().getId());
                }
            });
            MenuItem open = new MenuItem("Open profile");
            open.setOnAction(e -> shell.openVendor(row.getItem().getId()));
            MenuItem network = new MenuItem("Show in trust network");
            network.setOnAction(e -> shell.openNetworkAround(row.getItem().getId()));
            MenuItem edit = new MenuItem("Edit…");
            edit.setOnAction(e -> editVendor(row.getItem()));
            MenuItem delete = new MenuItem("Delete…");
            delete.setOnAction(e -> deleteVendor(row.getItem()));
            ContextMenu menu = new ContextMenu(open, network, edit, new SeparatorMenuItem(), delete);
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null).otherwise(menu));
            row.opacityProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> row.getItem() != null && !row.getItem().isActive() ? 0.55 : 1.0, row.itemProperty()));
            return row;
        });
    }

    private void applyFilter() {
        String text = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        Object band = bandFilter.getValue();
        Object cat = categoryFilter.getValue();
        filtered.setPredicate(v ->
                (text.isEmpty()
                        || v.getName().toLowerCase().contains(text)
                        || v.getPhone().contains(text)
                        || v.getMarketArea().toLowerCase().contains(text)
                        || v.getCity().toLowerCase().contains(text))
                        && (!(band instanceof RiskBand b) || v.getRiskBand() == b)
                        && (!(cat instanceof VendorCategory c) || v.getCategory() == c));
        countLabel.setText(filtered.size() + " of " + master.size() + " vendors");
    }

    @Override
    public void refresh() {
        try {
            master.setAll(ctx.vendors().findAll());
            applyFilter();
            table.sort();
        } catch (Exception e) {
            Ui.error("Could not load vendors", e);
        }
    }

    private Set<String> knownMarkets() {
        Set<String> markets = new HashSet<>();
        master.forEach(v -> markets.add(v.getMarketArea()));
        return markets;
    }

    private void addVendor() {
        new VendorDialog(ctx.vendors(), null, knownMarkets()).showAndWait().ifPresent(v ->
                shell.recalculateScores(() -> v.getName() + " added — starting score calculated"));
    }

    private void editVendor(Vendor v) {
        new VendorDialog(ctx.vendors(), v, knownMarkets()).showAndWait().ifPresent(saved -> {
            refresh();
            shell.toast("Saved changes to " + saved.getName());
        });
    }

    private void toggleActive(Vendor v) {
        try {
            ctx.vendors().setActive(v.getId(), !v.isActive());
            refresh();
            shell.toast(v.getName() + (v.isActive() ? " deactivated" : " re-activated"));
        } catch (Exception e) {
            Ui.error("Could not update vendor", e);
        }
    }

    private void deleteVendor(Vendor v) {
        if (!Ui.confirm("Delete vendor", "Delete " + v.getName() + "?\n\nTheir transactions, loans and vouches "
                + "will be removed permanently. Consider deactivating instead.", "Delete")) {
            return;
        }
        try {
            ctx.vendors().delete(v.getId());
            shell.recalculateScores(() -> v.getName() + " deleted");
        } catch (Exception e) {
            Ui.error("Could not delete vendor", e);
        }
    }
}
