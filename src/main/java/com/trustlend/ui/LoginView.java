package com.trustlend.ui;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.User;
import com.trustlend.service.AuthService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Two-panel welcome screen: brand story on the left, and on the right either the sign-in form
 * or the create-account form. Every account gets its own empty workspace.
 */
public class LoginView extends HBox {

    private final AuthService auth;
    /** Called with the logged-in user and {@code true} if the account was just created. */
    private final BiConsumer<User, Boolean> onSuccess;

    // sign in
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label loginError = Ui.label("", "error-text");
    private final Button signIn = new Button("Sign in");
    // sign up
    private final TextField newName = new TextField();
    private final TextField newUsername = new TextField();
    private final PasswordField newPassword = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final Label signupError = Ui.label("", "error-text");

    private final StackPane forms = new StackPane();
    private final VBox signInForm;
    private final VBox signUpForm;

    public LoginView(AuthService auth, BiConsumer<User, Boolean> onSuccess, String notice) {
        this.auth = auth;
        this.onSuccess = onSuccess;

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
                Ui.label("◆  Vouches from fellow vendors, as a weighted trust graph", "login-point"),
                Ui.label("◆  Your own private workspace — load demo data or start from zero", "login-point"));
        VBox brand = new VBox(18, mark, Ui.vSpacer(), hero, tag, points, Ui.vSpacer(),
                Ui.label("Java · JavaFX · JDBC/SQLite", "login-point"));
        brand.getStyleClass().add("login-brand");
        brand.setPrefWidth(520);
        HBox.setHgrow(brand, Priority.ALWAYS);

        // ---- right: forms
        signInForm = buildSignIn(notice);
        signUpForm = buildSignUp();
        forms.getChildren().addAll(signUpForm, signInForm);
        VBox right = new VBox(forms);
        right.setAlignment(Pos.CENTER);
        right.getStyleClass().add("login-form");
        right.setPrefWidth(480);

        getChildren().addAll(brand, right);
        showSignIn();
    }

    private VBox buildSignIn(String notice) {
        username.setPromptText("Username");
        password.setPromptText("Password");
        signIn.getStyleClass().add("btn-primary");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setOnAction(e -> signIn());

        Hyperlink create = new Hyperlink("New here? Create an account");
        create.setOnAction(e -> showSignUp());

        VBox form = new VBox(14, Ui.label("Welcome back", "page-title"), Ui.label("Sign in to your lending desk", "page-subtitle"),
                Ui.label("USERNAME", "form-label"), username,
                Ui.label("PASSWORD", "form-label"), password,
                loginError, signIn, create);
        if (notice != null && !notice.isBlank()) {
            Label hint = Ui.label(notice, "login-hint");
            hint.setWrapText(true);
            hint.setMaxWidth(Double.MAX_VALUE);
            form.getChildren().add(hint);
        }
        form.setMaxWidth(340);
        form.setAlignment(Pos.CENTER_LEFT);
        return form;
    }

    private VBox buildSignUp() {
        newName.setPromptText("e.g. Priya Sharma");
        newUsername.setPromptText("3-20 letters or digits");
        newPassword.setPromptText("At least 6 characters, a letter and a number");
        confirm.setPromptText("Type the password again");
        Button create = new Button("Create account");
        create.getStyleClass().add("btn-primary");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> signUp());
        confirm.setOnAction(e -> signUp());

        Hyperlink back = new Hyperlink("Already have an account? Sign in");
        back.setOnAction(e -> showSignIn());

        Label note = Ui.label("You get a private, empty workspace. Load the demo community or add your own vendors.",
                "muted", "small");
        note.setWrapText(true);

        VBox form = new VBox(10, Ui.label("Create your account", "page-title"), note,
                Ui.label("FULL NAME", "form-label"), newName,
                Ui.label("USERNAME", "form-label"), newUsername,
                Ui.label("PASSWORD", "form-label"), newPassword,
                Ui.label("CONFIRM PASSWORD", "form-label"), confirm,
                signupError, create, back);
        form.setMaxWidth(340);
        form.setAlignment(Pos.CENTER_LEFT);
        return form;
    }

    private void showSignIn() {
        signInForm.setVisible(true);
        signUpForm.setVisible(false);
        signIn.setDefaultButton(true);
        username.requestFocus();
    }

    private void showSignUp() {
        signInForm.setVisible(false);
        signUpForm.setVisible(true);
        signIn.setDefaultButton(false);       // Enter in the sign-up form must not sign in
        signupError.setText("");
        newName.requestFocus();
    }

    private void signIn() {
        clearErrors(username, password);
        try {
            onSuccess.accept(auth.login(username.getText(), password.getText()), false);
        } catch (ValidationException ex) {
            loginError.setText(ex.getMessage());
            (ex.getField().equals("username") ? username : password).getStyleClass().add("field-error");
            password.clear();
            password.requestFocus();
        } catch (Exception ex) {
            Ui.error("Login failed", ex);
        }
    }

    private void signUp() {
        Map<String, TextInputControl> fields = Map.of("name", newName, "username", newUsername,
                "password", newPassword, "confirm", confirm);
        clearErrors(newName, newUsername, newPassword, confirm);
        signupError.setText("");
        try {
            User u = auth.signUp(newName.getText(), newUsername.getText(), newPassword.getText(), confirm.getText());
            onSuccess.accept(u, true);
        } catch (ValidationException ex) {
            signupError.setText(ex.getMessage());
            TextInputControl f = fields.get(ex.getField());
            if (f != null) {
                f.getStyleClass().add("field-error");
                f.requestFocus();
            }
        } catch (Exception ex) {
            Ui.error("Could not create the account", ex);
        }
    }

    private static void clearErrors(TextInputControl... controls) {
        for (TextInputControl c : controls) c.getStyleClass().remove("field-error");
    }

    public void focusFirst() {
        username.requestFocus();
    }
}
