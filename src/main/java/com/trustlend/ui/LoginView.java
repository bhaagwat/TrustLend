package com.trustlend.ui;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.User;
import com.trustlend.service.AuthService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Two-panel sign-in screen: brand story on the left, form on the right. */
public class LoginView extends HBox {

    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label error = Ui.label("", "error-text");

    public LoginView(AuthService auth, Consumer<User> onSuccess) {
        // ---- left: brand
        StackPane mark = new StackPane(new Label("T"));
        mark.getStyleClass().add("brand-mark");
        Label hero = Ui.label("Credit built on\ncommunity trust.", "login-hero");
        hero.setWrapText(true);
        Label tag = Ui.label("TrustLend scores street vendors and small shopkeepers from the way they "
                + "actually do business — not from paperwork they don't have.", "login-tag");
        tag.setWrapText(true);
        VBox points = new VBox(10,
                Ui.label("◆  Consistency of daily transactions", "login-point"),
                Ui.label("◆  Repayment history on micro-loans", "login-point"),
                Ui.label("◆  Vouches from fellow vendors, as a weighted trust graph", "login-point"));
        VBox brand = new VBox(18, mark, Ui.vSpacer(), hero, tag, points, Ui.vSpacer(),
                Ui.label("Java · JavaFX · JDBC/SQLite", "login-point"));
        brand.getStyleClass().add("login-brand");
        brand.setPrefWidth(520);
        HBox.setHgrow(brand, Priority.ALWAYS);

        // ---- right: form
        Label title = Ui.label("Welcome back", "page-title");
        Label sub = Ui.label("Sign in to the lending desk", "page-subtitle");
        username.setPromptText("Username");
        password.setPromptText("Password");
        username.setText(AuthService.DEFAULT_ADMIN);

        Button signIn = new Button("Sign in");
        signIn.getStyleClass().add("btn-primary");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setDefaultButton(true);   // Enter key triggers it
        signIn.setOnAction(e -> {
            username.getStyleClass().remove("field-error");
            password.getStyleClass().remove("field-error");
            try {
                User u = auth.login(username.getText(), password.getText());
                onSuccess.accept(u);
            } catch (ValidationException ex) {
                error.setText(ex.getMessage());
                (ex.getField().equals("username") ? username : password).getStyleClass().add("field-error");
                password.clear();
                password.requestFocus();
            } catch (Exception ex) {
                Ui.error("Login failed", ex);
            }
        });

        Label hint = Ui.label("Demo login — username: admin   password: admin123", "login-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(14, title, sub,
                Ui.label("USERNAME", "form-label"), username,
                Ui.label("PASSWORD", "form-label"), password,
                error, signIn, hint);
        form.setMaxWidth(340);
        form.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(form);
        right.setAlignment(Pos.CENTER);
        right.getStyleClass().add("login-form");
        right.setPrefWidth(480);

        getChildren().addAll(brand, right);
        password.requestFocus();
    }

    public void focusPassword() {
        password.requestFocus();
    }
}
