package com.trustlend.ui.view;

import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.dialog.LoanDialog;
import com.trustlend.ui.dialog.RepaymentDialog;
import com.trustlend.ui.dialog.RippleDialog;
import com.trustlend.util.Formats;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Loan book: filter by status, record repayments, mark defaults. */
public class LoansView extends BaseView {

    private final ObservableList<Loan> master = FXCollections.observableArrayList();
    private final FilteredList<Loan> filtered = new FilteredList<>(master);
    private final TableView<Loan> table = new TableView<>();
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final Map<LoanStatus, ToggleButton> filterButtons = new EnumMap<>(LoanStatus.class);
    private final ToggleButton allButton = new ToggleButton();
    private final Label summary = Ui.label("", "muted");

    public LoansView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        Button newLoan = new Button("₹  New loan");
        newLoan.getStyleClass().add("btn-accent");
        newLoan.setOnAction(e -> issueLoan());
        setHeader("Loans", "Micro-loans priced by trust score — better score, cheaper credit", newLoan);

        allButton.setToggleGroup(filterGroup);
        allButton.getStyleClass().add("btn-ghost");
        allButton.setUserData(null);
        HBox filters = Ui.row(8, allButton);
        for (LoanStatus s : LoanStatus.values()) {
            ToggleButton b = new ToggleButton();
            b.setToggleGroup(filterGroup);
            b.setUserData(s);
            b.getStyleClass().add("btn-ghost");
            filterButtons.put(s, b);
            filters.getChildren().add(b);
        }
        filters.getChildren().addAll(Ui.hSpacer(), summary);
        filterGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) {
                old.setSelected(true);
                return;
            }
            LoanStatus s = (LoanStatus) now.getUserData();
            filtered.setPredicate(l -> s == null || l.getStatus() == s);
            filterGroup.getToggles().forEach(t -> ((ToggleButton) t).setStyle(t == now
                    ? "-fx-background-color: #1c2140; -fx-text-fill: white;" : ""));
        });

        buildTable();
        SortedList<Loan> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        Button repay = new Button("Record repayment");
        repay.getStyleClass().add("btn-primary");
        Button schedule = new Button("Repayment history");
        schedule.getStyleClass().add("btn-ghost");
        Button vendorBtn = new Button("Open vendor");
        vendorBtn.getStyleClass().add("btn-ghost");
        Button defaulted = new Button("Mark defaulted");
        defaulted.getStyleClass().add("btn-danger");
        repay.setOnAction(e -> selected().ifPresent(this::recordRepayment));
        schedule.setOnAction(e -> selected().ifPresent(this::showRepayments));
        vendorBtn.setOnAction(e -> selected().ifPresent(l -> shell.openVendor(l.getVendorId())));
        defaulted.setOnAction(e -> selected().ifPresent(this::markDefaulted));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, l) -> {
            boolean none = l == null;
            boolean open = !none && l.getStatus().isOpen();
            repay.setDisable(!open);
            defaulted.setDisable(!open);
            schedule.setDisable(none);
            vendorBtn.setDisable(none);
        });
        repay.setDisable(true);
        defaulted.setDisable(true);
        schedule.setDisable(true);
        vendorBtn.setDisable(true);

        VBox body = new VBox(14, filters, table, Ui.row(8, repay, schedule, vendorBtn, Ui.hSpacer(), defaulted));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(body);
        allButton.setSelected(true);
    }

    private Optional<Loan> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private void buildTable() {
        TableColumn<Loan, Number> id = new TableColumn<>("LOAN");
        id.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getId()));
        id.setCellFactory(c -> textCell(n -> "#" + n));
        id.setPrefWidth(70);

        TableColumn<Loan, String> vendor = new TableColumn<>("VENDOR");
        vendor.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getVendorName()));
        vendor.setPrefWidth(170);

        TableColumn<Loan, Number> principal = new TableColumn<>("PRINCIPAL");
        principal.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getPrincipal()));
        principal.setCellFactory(c -> textCell(n -> Formats.inr(n.doubleValue())));

        TableColumn<Loan, Number> rate = new TableColumn<>("RATE");
        rate.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getInterestRate()));
        rate.setCellFactory(c -> textCell(n -> n + "%"));
        rate.setPrefWidth(70);

        TableColumn<Loan, Number> score = new TableColumn<>("SCORE AT ISSUE");
        score.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getScoreAtIssue()));

        TableColumn<Loan, LocalDate> issued = new TableColumn<>("ISSUED");
        issued.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getIssueDate()));
        issued.setCellFactory(c -> textCell(Formats::date));

        TableColumn<Loan, LocalDate> due = new TableColumn<>("DUE");
        due.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getDueDate()));
        due.setCellFactory(c -> textCell(Formats::date));

        TableColumn<Loan, Number> progress = new TableColumn<>("REPAID");
        progress.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getProgress()));
        progress.setCellFactory(c -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar();
            private final Label text = Ui.label("", "small");

            @Override
            protected void updateItem(Number p, boolean empty) {
                super.updateItem(p, empty);
                Loan l = empty ? null : getTableRow().getItem();
                if (l == null) {
                    setGraphic(null);
                    return;
                }
                bar.setProgress(l.getProgress());
                bar.setPrefWidth(90);
                text.setText(Formats.inrCompact(l.getAmountRepaid()) + " / " + Formats.inrCompact(l.getTotalPayable()));
                setGraphic(new VBox(3, bar, text));
            }
        });
        progress.setPrefWidth(150);

        TableColumn<Loan, LoanStatus> status = new TableColumn<>("STATUS");
        status.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStatus()));
        status.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LoanStatus s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic(empty || s == null ? null : Ui.statusPill(s));
            }
        });

        table.getColumns().addAll(List.of(id, vendor, principal, rate, score, issued, due, progress, status));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No loans in this view"));
        table.setRowFactory(tv -> {
            TableRow<Loan> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) showRepayments(row.getItem());
            });
            return row;
        });
    }

    private static <T> TableCell<Loan, T> textCell(Function<T, String> f) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : f.apply(item));
            }
        };
    }

    @Override
    public void refresh() {
        try {
            List<Loan> loans = ctx.loans().listLoans();
            loans.sort(Comparator.comparing(Loan::getIssueDate).reversed());
            master.setAll(loans);
            Map<LoanStatus, Integer> counts = new EnumMap<>(LoanStatus.class);
            double outstanding = 0;
            for (Loan l : loans) {
                counts.merge(l.getStatus(), 1, Integer::sum);
                if (l.getStatus().isOpen()) outstanding += l.getOutstanding();
            }
            allButton.setText("All (" + loans.size() + ")");
            filterButtons.forEach((s, b) -> b.setText(s.getLabel() + " (" + counts.getOrDefault(s, 0) + ")"));
            summary.setText("Outstanding: " + Formats.inr(outstanding));
        } catch (Exception e) {
            Ui.error("Could not load loans", e);
        }
    }

    private void issueLoan() {
        try {
            List<Vendor> vendors = ctx.vendors().findAll().stream().filter(Vendor::isActive).toList();
            new LoanDialog(ctx, vendors, null).showAndWait().ifPresent(l ->
                    shell.recalculateScores(() -> "Loan #" + l.getId() + " issued for " + Formats.inr(l.getPrincipal())));
        } catch (Exception e) {
            Ui.error("Could not open loan form", e);
        }
    }

    private void recordRepayment(Loan loan) {
        new RepaymentDialog(ctx, loan).showAndWait().ifPresent(r -> shell.recalculateScores(() ->
                "Payment of " + Formats.inr(r.getAmount()) + " recorded" + (r.isOnTime() ? " (on time)" : " (late)")));
    }

    private void markDefaulted(Loan loan) {
        if (Ui.confirm("Mark as defaulted", "Mark loan #" + loan.getId() + " of " + loan.getVendorName()
                + " as defaulted?\n\nTheir score will be capped and everyone who vouched for them will lose "
                + "some vouching credibility.", "Mark defaulted")) {
            try {
                long borrower = loan.getVendorId();
                java.util.Map<Long, com.trustlend.model.ScoreBreakdown> before = new java.util.HashMap<>(ctx.scores().latest());
                java.util.Set<Long> vouchers = new java.util.HashSet<>(ctx.scores().graph().inNeighbours(borrower).keySet());
                ctx.loans().markDefaulted(loan.getId());
                shell.recalculateScores(() -> "Loan #" + loan.getId() + " marked defaulted; network updated", () -> {
                    try {
                        java.util.Map<Long, com.trustlend.model.Vendor> vendors = new java.util.HashMap<>();
                        ctx.vendors().findAll().forEach(v -> vendors.put(v.getId(), v));
                        var rows = RippleDialog.rows(borrower, vouchers, vendors, before, ctx.scores().latest());
                        new RippleDialog(loan.getVendorName(), rows).showAndWait()
                                .filter(Boolean::booleanValue)
                                .ifPresent(b -> shell.showRippleOnNetwork(borrower,
                                        rows.stream().map(r -> r.vendor().getId()).toList()));
                    } catch (Exception e) {
                        Ui.error("Could not show the ripple effect", e);
                    }
                });
            } catch (Exception e) {
                Ui.error("Could not update loan", e);
            }
        }
    }

    private void showRepayments(Loan loan) {
        try {
            List<Repayment> reps = ctx.loans().repaymentsOf(loan.getId());
            ListView<String> list = new ListView<>();
            int n = 1;
            for (Repayment r : reps) {
                list.getItems().add(String.format("%2d.  %s   paid %s   (due %s)  %s", n++, Formats.inr(r.getAmount()),
                        Formats.date(r.getPaidDate()), Formats.date(r.getDueDate()), r.isOnTime() ? "✔ on time" : "⚠ late"));
            }
            if (reps.isEmpty()) list.getItems().add("No repayments yet");
            list.setPrefSize(520, 320);
            Alert a = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.CLOSE);
            a.setTitle("Repayment history");
            a.setHeaderText("Loan #" + loan.getId() + " · " + loan.getVendorName() + " · " + loan.getStatus().getLabel()
                    + "\nRepaid " + Formats.inr(loan.getAmountRepaid()) + " of " + Formats.inr(loan.getTotalPayable()));
            a.getDialogPane().setContent(list);
            Ui.style(a.getDialogPane());
            a.showAndWait();
        } catch (Exception e) {
            Ui.error("Could not load repayments", e);
        }
    }
}
