package com.trustlend.ui.view;

import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.dialog.TransactionDialog;
import com.trustlend.util.Formats;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ledger of every business transaction, with vendor / type / date-range filters. */
public class TransactionsView extends BaseView {

    private static final String ALL_VENDORS = "All vendors";
    private static final String ALL_TYPES = "All types";

    private final ObservableList<Transaction> master = FXCollections.observableArrayList();
    private final FilteredList<Transaction> filtered = new FilteredList<>(master);
    private final TableView<Transaction> table = new TableView<>();
    private final ComboBox<Object> vendorFilter = new ComboBox<>();
    private final ComboBox<Object> typeFilter = new ComboBox<>();
    private final DatePicker from = new DatePicker(LocalDate.now().minusDays(30));
    private final DatePicker to = new DatePicker(LocalDate.now());
    private final Label totals = Ui.label("", "muted");
    private final Map<Long, String> names = new HashMap<>();
    private List<Vendor> vendors = List.of();

    public TransactionsView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        Button add = new Button("+  Record transaction");
        add.getStyleClass().add("btn-primary");
        add.setOnAction(e -> new TransactionDialog(ctx, vendors.stream().filter(Vendor::isActive).toList(), null)
                .showAndWait().ifPresent(t -> shell.recalculateScores(() -> "Transaction saved; scores updated")));
        setHeader("Transactions", "Daily sales, stock purchases and savings — the raw signal behind consistency", add);

        typeFilter.getItems().add(ALL_TYPES);
        typeFilter.getItems().addAll((Object[]) Transaction.Type.values());
        typeFilter.getSelectionModel().selectFirst();
        vendorFilter.setPrefWidth(220);
        from.setPrefWidth(140);
        to.setPrefWidth(140);
        vendorFilter.valueProperty().addListener((o, a, b) -> applyFilter());
        typeFilter.valueProperty().addListener((o, a, b) -> applyFilter());
        from.valueProperty().addListener((o, a, b) -> applyFilter());
        to.valueProperty().addListener((o, a, b) -> applyFilter());

        HBox filters = Ui.row(10, vendorFilter, typeFilter, Ui.label("From", "form-label"), from,
                Ui.label("to", "form-label"), to, Ui.hSpacer(), totals);

        TableColumn<Transaction, LocalDate> date = new TableColumn<>("DATE");
        date.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getDate()));
        date.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : Formats.date(d));
            }
        });
        TableColumn<Transaction, String> vendor = new TableColumn<>("VENDOR");
        vendor.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(names.getOrDefault(c.getValue().getVendorId(), "?")));
        TableColumn<Transaction, Transaction.Type> type = new TableColumn<>("TYPE");
        type.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getType()));
        type.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Transaction.Type t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setGraphic(null);
                    return;
                }
                Label chip = Ui.label(t.getLabel(), "chip");
                if (t == Transaction.Type.LOAN_DISBURSAL) chip.setStyle("-fx-background-color: #e1effe; -fx-text-fill: #1a56db;");
                if (t == Transaction.Type.DEPOSIT) chip.setStyle("-fx-background-color: #dcf3e6; -fx-text-fill: #0b7a43;");
                setGraphic(chip);
            }
        });
        TableColumn<Transaction, Number> amount = new TableColumn<>("AMOUNT");
        amount.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getAmount()));
        amount.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Number n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : Formats.inr(n.doubleValue()));
                setStyle("-fx-font-weight: bold;");
            }
        });
        TableColumn<Transaction, String> note = new TableColumn<>("NOTE");
        note.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getNote()));
        table.getColumns().addAll(List.of(date, vendor, type, amount, note));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transactions in this range"));

        SortedList<Transaction> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        Button delete = new Button("Delete selected");
        delete.getStyleClass().add("btn-danger");
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(e -> deleteSelected());

        VBox body = new VBox(14, filters, table, Ui.row(8, Ui.hSpacer(), delete));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(body);
    }

    private void applyFilter() {
        Object v = vendorFilter.getValue();
        Object t = typeFilter.getValue();
        LocalDate f = from.getValue();
        LocalDate u = to.getValue();
        filtered.setPredicate(tx ->
                (!(v instanceof Vendor vendor) || tx.getVendorId() == vendor.getId())
                        && (!(t instanceof Transaction.Type type) || tx.getType() == type)
                        && (f == null || !tx.getDate().isBefore(f))
                        && (u == null || !tx.getDate().isAfter(u)));
        double sales = 0;
        for (Transaction tx : filtered) {
            if (tx.getType() == Transaction.Type.SALE) sales += tx.getAmount();
        }
        totals.setText(filtered.size() + " entries · sales " + Formats.inr(sales));
    }

    @Override
    public void refresh() {
        try {
            vendors = ctx.vendors().findAll();
            names.clear();
            vendors.forEach(v -> names.put(v.getId(), v.getName()));
            Object current = vendorFilter.getValue();
            vendorFilter.getItems().setAll(ALL_VENDORS);
            vendorFilter.getItems().addAll(vendors);
            vendorFilter.setValue(current instanceof Vendor cv && vendors.contains(cv) ? cv : ALL_VENDORS);
            master.setAll(ctx.transactions().all());
            applyFilter();
        } catch (Exception e) {
            Ui.error("Could not load transactions", e);
        }
    }

    private void deleteSelected() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null || !Ui.confirm("Delete transaction", "Delete " + t + "?", "Delete")) return;
        try {
            ctx.transactions().delete(t.getId());
            shell.recalculateScores(() -> "Transaction deleted");
        } catch (Exception e) {
            Ui.error("Could not delete", e);
        }
    }
}
