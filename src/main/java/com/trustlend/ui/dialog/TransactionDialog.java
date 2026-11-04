package com.trustlend.ui.dialog;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.util.Validators;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;

/** Record a sale, stock purchase or savings deposit. */
public class TransactionDialog extends FormDialog<Transaction> {

    private final AppContext ctx;
    private final ComboBox<Vendor> vendor;
    private final ComboBox<Transaction.Type> type = new ComboBox<>(FXCollections.observableArrayList(
            Transaction.Type.SALE, Transaction.Type.PURCHASE, Transaction.Type.DEPOSIT));
    private final TextField amount = new TextField();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final TextArea note = new TextArea();

    public TransactionDialog(AppContext ctx, List<Vendor> vendors, Vendor preselected) {
        super("Record transaction", "Add a business transaction", "Save");
        this.ctx = ctx;
        vendor = new ComboBox<>(FXCollections.observableArrayList(vendors));
        vendor.setPromptText("Choose vendor");
        type.setValue(Transaction.Type.SALE);
        amount.setPromptText("Amount in ₹");
        amount.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().matches("[0-9,.]{0,10}") ? c : null));
        note.setPrefRowCount(2);
        note.setWrapText(true);
        note.setPromptText("Optional note");

        addRow("Vendor", "vendor", vendor);
        addRow("Type", "type", type);
        addRow("Amount (₹)", "amount", amount);
        addRow("Date", "date", date);
        addRow("Note", "note", note);
        if (preselected != null) {
            vendor.setValue(preselected);
            vendor.setDisable(true);
        }
    }

    @Override
    protected Transaction submit() throws Exception {
        if (vendor.getValue() == null) throw new ValidationException("vendor", "Choose a vendor");
        double a = Validators.parseAmount("amount", amount.getText());
        return ctx.transactions().record(vendor.getValue().getId(), a, type.getValue(), date.getValue(), note.getText());
    }
}
