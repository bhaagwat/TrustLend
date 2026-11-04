package com.trustlend.ui;

import com.trustlend.exception.TrustLendException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.RiskBand;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Small static helpers so view code stays readable. */
public final class Ui {

    private static final Logger LOG = Logger.getLogger(Ui.class.getName());
    private static Window owner;

    private Ui() { }

    public static void setOwner(Window window) {
        owner = window;
    }

    // ------------------------------------------------------------------ factories

    public static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    public static Region hSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region vSpacer() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        return box;
    }

    public static VBox titledCard(String title, Node... children) {
        VBox box = card();
        box.getChildren().add(label(title, "section-title"));
        box.getChildren().addAll(children);
        return box;
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static Label bandPill(RiskBand band) {
        Label l = label(band.getLabel(), "pill", band.styleClass());
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    public static Label statusPill(LoanStatus status) {
        Label l = label(status.getLabel(), "pill", "status-" + status.name().toLowerCase());
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    public static Insets pad(double v) {
        return new Insets(v);
    }

    // ------------------------------------------------------------------ dialogs

    public static void info(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        prepare(a, title);
        a.showAndWait();
    }

    public static boolean confirm(String title, String message, String confirmText) {
        ButtonType yes = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, yes, ButtonType.CANCEL);
        prepare(a, title);
        Optional<ButtonType> result = a.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    /** Shows a friendly message; unexpected exceptions get an expandable stack trace. */
    public static void error(String title, Throwable t) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        prepare(a, title);
        if (t instanceof ValidationException || t instanceof TrustLendException) {
            a.setContentText(t.getMessage());
        } else {
            LOG.log(Level.SEVERE, title, t);
            a.setContentText(t.getClass().getSimpleName() + ": " + t.getMessage());
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            TextArea area = new TextArea(sw.toString());
            area.setEditable(false);
            area.setPrefRowCount(12);
            a.getDialogPane().setExpandableContent(area);
        }
        a.showAndWait();
    }

    private static void prepare(Alert a, String title) {
        a.setTitle(title);
        a.setHeaderText(title);
        if (owner != null) a.initOwner(owner);
        a.getDialogPane().getStylesheets().add(stylesheet());
    }

    public static String stylesheet() {
        return Ui.class.getResource("/styles/app.css").toExternalForm();
    }

    // ------------------------------------------------------------------ background work

    /**
     * Runs {@code work} on a background thread with a JavaFX {@link Task}, then calls
     * {@code onSuccess} or {@code onError} back on the JavaFX Application Thread.
     */
    public static <T> Task<T> async(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "trustlend-worker");
        thread.setDaemon(true);
        thread.start();
        return task;
    }
}
