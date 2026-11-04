package com.trustlend.ui.dialog;

import com.trustlend.exception.ValidationException;
import com.trustlend.ui.Ui;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for data-entry dialogs.
 *
 * The OK button gets an event <em>filter</em>: {@link #submit()} runs first, and if it throws a
 * {@link ValidationException} the event is consumed, so the dialog stays open, the offending
 * field turns red and the message is shown inline. Only valid input ever closes the dialog.
 *
 * @param <R> what the dialog returns
 */
public abstract class FormDialog<R> extends Dialog<R> {

    protected final GridPane grid = new GridPane();
    private final Label errorLabel = Ui.label("", "error-text");
    private final Map<String, Control> fields = new HashMap<>();
    private R result;
    private int rows = 0;

    protected FormDialog(String title, String header, String okText) {
        setTitle(title);
        setHeaderText(header);
        getDialogPane().getStylesheets().add(Ui.stylesheet());

        grid.setHgap(14);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints(130);
        ColumnConstraints inputs = new ColumnConstraints(280, 320, Double.MAX_VALUE);
        inputs.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, inputs);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(440);

        VBox content = new VBox(12, grid, errorLabel);
        getDialogPane().setContent(content);

        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        Button okButton = (Button) getDialogPane().lookupButton(ok);
        okButton.getStyleClass().add("btn-primary");
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            clearErrors();
            try {
                result = submit();
            } catch (ValidationException ex) {
                showError(ex.getField(), ex.getMessage());
                event.consume();          // keep dialog open
            } catch (Exception ex) {
                showError(null, ex.getMessage());
                event.consume();
            }
        });
        setResultConverter(button -> button == ok ? result : null);
    }

    /** Adds "Label | control" as the next grid row and registers it for error highlighting. */
    protected void addRow(String label, String fieldKey, Control control) {
        Label l = Ui.label(label, "form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        grid.addRow(rows++, l, control);
        if (fieldKey != null) fields.put(fieldKey, control);
    }

    protected void addFullRow(Node node) {
        grid.add(node, 0, rows++, 2, 1);
    }

    /** Validate input and perform the action. Throw ValidationException to keep the dialog open. */
    protected abstract R submit() throws Exception;

    protected void showError(String field, String message) {
        errorLabel.setText("⚠  " + message);
        Control c = field == null ? null : fields.get(field);
        if (c != null) {
            c.getStyleClass().add("field-error");
            c.requestFocus();
        }
    }

    private void clearErrors() {
        errorLabel.setText("");
        fields.values().forEach(c -> c.getStyleClass().remove("field-error"));
    }
}
