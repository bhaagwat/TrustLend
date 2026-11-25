package com.trustlend.ui;

import com.trustlend.config.AppConfig;
import com.trustlend.service.AppContext;
import com.trustlend.util.QrCode;
import com.trustlend.web.PhoneServer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * "Open on a phone": starts the built-in phone page and shows a QR code to scan. A borrower logs
 * in on their phone, confirms repayments or uploads a statement, and the laptop updates live.
 */
public final class PhoneAccess {

    private PhoneAccess() { }

    public static void show(AppContext ctx, MainWindow shell) {
        PhoneServer server;
        try {
            int port = AppConfig.getInstance().getInt("phone.port");
            server = PhoneServer.start(ctx, port, message -> Platform.runLater(() -> {
                shell.refreshAll();
                shell.toastInfo(message);
            }));
        } catch (Exception e) {
            Ui.error("Could not start the phone page (is port " + AppConfig.getInstance().get("phone.port") + " in use?)", e);
            return;
        }
        String url = server.address();

        Dialog<Void> d = new Dialog<>();
        d.setTitle("Open TrustLend on a phone");
        d.setHeaderText(null);
        Ui.style(d.getDialogPane());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Canvas qr = drawQr(url, 250);
        StackPane qrBox = new StackPane(qr);
        qrBox.getStyleClass().add("qr-box");

        TextField address = new TextField(url);
        address.setEditable(false);
        address.getStyleClass().add("phone-url");

        Label title = Ui.label("Scan with your phone camera", "dialog-title");
        Label steps = Ui.label("1. Connect the phone to the SAME Wi-Fi as this laptop.\n"
                + "2. Scan the code (or type the address in the phone's browser).\n"
                + "3. Log in as a borrower — for the demo: javed / demo123.\n"
                + "4. Confirm a repayment or upload a statement: this laptop updates instantly.", "small");
        steps.setWrapText(true);
        Label firewall = Ui.label("If Windows asks whether Java may use the network, allow it on private networks. "
                + "Works on the local network only.", "muted", "small");
        firewall.setWrapText(true);
        VBox text = new VBox(10, title, steps, Ui.label("ADDRESS", "form-label"), address, firewall);
        if (url.contains("localhost")) {
            Label warn = Ui.label("No Wi-Fi address found — connect this laptop to Wi-Fi first.", "error-text");
            warn.setWrapText(true);
            text.getChildren().add(warn);
        }
        Button browser = new Button("Open it on this computer");
        browser.getStyleClass().add("btn-ghost");
        browser.setOnAction(e -> openInBrowser(url.replaceFirst("//[^:/]+", "//localhost")));
        Button stop = new Button("Stop phone access");
        stop.getStyleClass().add("btn-danger");
        stop.setOnAction(e -> {
            PhoneServer.stopIfRunning();
            d.close();
            shell.toastInfo("Phone access stopped");
        });
        text.getChildren().add(new HBox(10, browser, stop));
        text.setPrefWidth(380);

        HBox content = new HBox(24, qrBox, text);
        content.setAlignment(Pos.CENTER_LEFT);
        d.getDialogPane().setContent(content);
        d.show();
    }

    /** Draws the QR code with a white quiet zone of 4 modules. */
    private static Canvas drawQr(String text, double pixels) {
        QrCode qr = QrCode.encode(text);
        int modules = qr.size() + 8;
        double m = Math.floor(pixels / modules);
        Canvas c = new Canvas(m * modules, m * modules);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
        g.setFill(Color.web("#1c2140"));
        for (int y = 0; y < qr.size(); y++) {
            for (int x = 0; x < qr.size(); x++) {
                if (qr.isDark(x, y)) g.fillRect((x + 4) * m, (y + 4) * m, m, m);
            }
        }
        return c;
    }

    private static void openInBrowser(String url) {
        Thread t = new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Exception e) {
                Platform.runLater(() -> Ui.error("Could not open the browser", e));
            }
        }, "open-browser");
        t.setDaemon(true);
        t.start();
    }
}
