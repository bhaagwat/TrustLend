package com.trustlend.ui;

import com.trustlend.exception.ValidationException;
import com.trustlend.model.User;
import com.trustlend.model.VendorCategory;
import com.trustlend.service.AppContext;
import com.trustlend.service.DemoAccounts;
import com.trustlend.ui.component.Icons;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Welcome screen. Pick who you are — Borrower, Lender or TrustLend HQ — then sign in, create an
 * account, or click one of the demo accounts.
 */
public class LoginView extends HBox {

    private final AppContext ctx;
    /** Called with the logged-in user and {@code true} if the account was just created. */
    private final BiConsumer<User, Boolean> onSuccess;

    private final ToggleGroup roles = new ToggleGroup();
    private final ToggleButton asBorrower = roleCard("Borrower", "Street vendor, shopkeeper, worker", "vendors");
    private final ToggleButton asLender = roleCard("Lender", "Moneylender, SHG, wholesaler, MFI", "loans");
    private final ToggleButton asHq = roleCard("TrustLend HQ", "Oversees the whole community", "shield");

    // sign in
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label loginError = Ui.label("", "error-text");
    private final Button signIn = new Button("Sign in");
    private final Hyperlink createLink = new Hyperlink();
    private final FlowPane demoChips = new FlowPane(8, 8);
    private final Label signInTitle = Ui.label("", "page-title");

    // borrower sign-up
    private final TextField bName = new TextField();
    private final TextField bPhone = new TextField();
    private final ComboBox<VendorCategory> bCategory = new ComboBox<>(FXCollections.observableArrayList(VendorCategory.values()));
    private final TextField bMarket = new TextField();
    private final TextField bCity = new TextField("Delhi");
    private final Spinner<Integer> bYears = new Spinner<>(0, 60, 3);
    private final TextField bUser = new TextField();
    private final PasswordField bPass = new PasswordField();
    private final PasswordField bConfirm = new PasswordField();
    private final Label bError = Ui.label("", "error-text");

    // lender sign-up
    private final TextField lName = new TextField();
    private final TextField lOrg = new TextField();
    private final ComboBox<String> lType = new ComboBox<>(FXCollections.observableArrayList(
            "Self-help group", "Moneylender", "Wholesaler (stock on credit)", "Microfinance company", "Cooperative / chit fund"));
    private final TextField lUser = new TextField();
    private final PasswordField lPass = new PasswordField();
    private final PasswordField lConfirm = new PasswordField();
    private final Label lError = Ui.label("", "error-text");

    private final VBox signInForm;
    private final VBox borrowerForm;
    private final VBox lenderForm;
    private final VBox formHolder = new VBox();

    public LoginView(AppContext ctx, BiConsumer<User, Boolean> onSuccess) {
        this.ctx = ctx;
        this.onSuccess = onSuccess;

        // ---- left: brand
        StackPane mark = new StackPane(new Label("T"));
        mark.getStyleClass().add("brand-mark");
        Label hero = Ui.label("Credit built on\ncommunity trust.", "login-hero");
        hero.setWrapText(true);
        Label tag = Ui.label("Street vendors have no paperwork, but they do have a history: daily UPI sales, loans "
                + "repaid to moneylenders and SHGs, neighbours who trust them. TrustLend turns that history into a "
                + "credit score every lender can use.", "login-tag");
        tag.setWrapText(true);
        VBox points = new VBox(10,
                Ui.label("◆  Borrowers upload their UPI statements and confirm repayments", "login-point"),
                Ui.label("◆  Lenders report repayments — they count fully once the borrower confirms", "login-point"),
                Ui.label("◆  Fraud checks on every upload; HQ settles disputes", "login-point"),
                Ui.label("◆  Vouches from trusted neighbours flow through a trust network", "login-point"));
        for (javafx.scene.Node n : points.getChildren()) ((Label) n).setWrapText(true);
        VBox brand = new VBox(18, mark, Ui.vSpacer(), hero, tag, points, Ui.vSpacer(),
                Ui.label("Java · JavaFX · JDBC/SQLite", "login-point"));
        brand.getStyleClass().add("login-brand");
        brand.setPrefWidth(500);
        HBox.setHgrow(brand, Priority.ALWAYS);

        // ---- right
        for (ToggleButton t : new ToggleButton[]{asBorrower, asLender, asHq}) {
            t.setToggleGroup(roles);
            HBox.setHgrow(t, Priority.ALWAYS);
            t.setMaxWidth(Double.MAX_VALUE);
        }
        roles.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) old.setSelected(true);
            else showSignIn();
        });
        HBox roleRow = new HBox(8, asBorrower, asLender, asHq);

        signInForm = buildSignIn();
        borrowerForm = buildBorrowerForm();
        lenderForm = buildLenderForm();

        VBox right = new VBox(16, Ui.label("LOG IN AS", "form-label"), roleRow, formHolder);
        right.setMaxWidth(440);
        right.setAlignment(Pos.TOP_LEFT);
        ScrollPane scroll = new ScrollPane(right);
        scroll.setFitToWidth(true);
        VBox rightPane = new VBox(scroll);
        rightPane.getStyleClass().add("login-form");
        rightPane.setPrefWidth(520);
        rightPane.setMinWidth(470);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(brand, rightPane);
        asBorrower.setSelected(true);
    }

    private static ToggleButton roleCard(String title, String sub, String icon) {
        Label t = Ui.label(title, "role-title");
        Label s = Ui.label(sub, "role-sub");
        s.setWrapText(true);
        VBox box = new VBox(4, Icons.of(icon, 22), t, s);
        ToggleButton b = new ToggleButton();
        b.setGraphic(box);
        b.getStyleClass().add("role-card");
        return b;
    }

    private User.Role role() {
        return asLender.isSelected() ? User.Role.LENDER : asHq.isSelected() ? User.Role.ADMIN : User.Role.BORROWER;
    }

    // ------------------------------------------------------------------ sign in

    private VBox buildSignIn() {
        username.setPromptText("Username");
        password.setPromptText("Password");
        signIn.getStyleClass().add("btn-primary");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setOnAction(e -> signIn());
        createLink.setOnAction(e -> formHolder.getChildren().setAll(role() == User.Role.LENDER ? lenderForm : borrowerForm));
        loginError.setWrapText(true);
        VBox form = new VBox(10, signInTitle, Ui.label("USERNAME", "form-label"), username,
                Ui.label("PASSWORD", "form-label"), password, loginError, signIn, createLink,
                Ui.label("DEMO ACCOUNTS — CLICK TO LOG IN", "form-label"), demoChips);
        return form;
    }

    private void showSignIn() {
        User.Role r = role();
        signInTitle.setText(switch (r) {
            case LENDER -> "Lender sign in";
            case ADMIN, OFFICER -> "TrustLend HQ sign in";
            default -> "Borrower sign in";
        });
        createLink.setText(r == User.Role.LENDER ? "New lender? Register your SHG / business" : "New here? Create a borrower account");
        createLink.setVisible(r != User.Role.ADMIN);
        createLink.setManaged(r != User.Role.ADMIN);
        loginError.setText("");
        demoChips.getChildren().clear();
        for (String[] d : DemoAccounts.LOGINS) {
            if (!d[1].equals(r == User.Role.OFFICER ? "ADMIN" : r.name())) continue;
            Button chip = new Button(d[0] + "  ·  " + d[2]);
            chip.getStyleClass().add("demo-chip");
            chip.setOnAction(e -> {
                username.setText(d[0]);
                password.setText(d[0].equals("admin") ? DemoAccounts.HQ_PASSWORD : DemoAccounts.PASSWORD);
                signIn();
            });
            demoChips.getChildren().add(chip);
        }
        formHolder.getChildren().setAll(signInForm);
        signIn.setDefaultButton(true);
        username.requestFocus();
    }

    private void signIn() {
        clearErrors(username, password);
        try {
            onSuccess.accept(ctx.auth().login(username.getText(), password.getText(), role()), false);
        } catch (ValidationException ex) {
            loginError.setText(ex.getMessage());
            (ex.getField().equals("username") ? username : password).getStyleClass().add("field-error");
            password.requestFocus();
        } catch (Exception ex) {
            Ui.error("Login failed", ex);
        }
    }

    // ------------------------------------------------------------------ sign up

    private VBox buildBorrowerForm() {
        bName.setPromptText("e.g. Javed Ansari");
        bPhone.setPromptText("10-digit mobile number");
        bCategory.setPromptText("What do you sell?");
        bCategory.setMaxWidth(Double.MAX_VALUE);
        bMarket.setPromptText("e.g. Chandni Chowk");
        bUser.setPromptText("3-20 letters or digits");
        bPass.setPromptText("At least 6 characters, a letter and a number");
        bConfirm.setPromptText("Type the password again");
        bYears.setEditable(true);
        bYears.setMaxWidth(Double.MAX_VALUE);
        Button create = new Button("Create borrower account");
        create.getStyleClass().add("btn-primary");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> signUpBorrower());
        Hyperlink back = new Hyperlink("Already have an account? Sign in");
        back.setOnAction(e -> showSignIn());
        bError.setWrapText(true);
        Label note = Ui.label("You get a TrustLend ID to give your lenders. Then upload your UPI statement to start building your score.",
                "muted", "small");
        note.setWrapText(true);
        return new VBox(8, Ui.label("Create a borrower account", "page-title"), note,
                Ui.label("FULL NAME", "form-label"), bName, Ui.label("MOBILE NUMBER", "form-label"), bPhone,
                Ui.label("BUSINESS", "form-label"), bCategory, Ui.label("MARKET / AREA", "form-label"), bMarket,
                Ui.label("CITY", "form-label"), bCity, Ui.label("YEARS IN BUSINESS", "form-label"), bYears,
                Ui.label("USERNAME", "form-label"), bUser, Ui.label("PASSWORD", "form-label"), bPass,
                Ui.label("CONFIRM PASSWORD", "form-label"), bConfirm, bError, create, back);
    }

    private VBox buildLenderForm() {
        lName.setPromptText("Your name");
        lOrg.setPromptText("e.g. Jagriti Mahila Self-Help Group");
        lType.setPromptText("What kind of lender are you?");
        lType.setMaxWidth(Double.MAX_VALUE);
        lUser.setPromptText("3-20 letters or digits");
        lPass.setPromptText("At least 6 characters, a letter and a number");
        lConfirm.setPromptText("Type the password again");
        Button create = new Button("Register as a lender");
        create.getStyleClass().add("btn-primary");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> signUpLender());
        Hyperlink back = new Hyperlink("Already registered? Sign in");
        back.setOnAction(e -> showSignIn());
        lError.setWrapText(true);
        Label note = Ui.label("Lend through TrustLend, report loans you gave before, and record repayments. "
                + "Your credibility grows as borrowers confirm your reports.", "muted", "small");
        note.setWrapText(true);
        return new VBox(8, Ui.label("Register as a lender", "page-title"), note,
                Ui.label("YOUR NAME", "form-label"), lName, Ui.label("ORGANISATION", "form-label"), lOrg,
                Ui.label("TYPE", "form-label"), lType, Ui.label("USERNAME", "form-label"), lUser,
                Ui.label("PASSWORD", "form-label"), lPass, Ui.label("CONFIRM PASSWORD", "form-label"), lConfirm,
                lError, create, back);
    }

    private void signUpBorrower() {
        Map<String, TextInputControl> fields = new HashMap<>(Map.of("name", bName, "phone", bPhone, "market area", bMarket,
                "city", bCity, "username", bUser, "password", bPass, "confirm", bConfirm));
        clearErrors(fields.values().toArray(TextInputControl[]::new));
        bError.setText("");
        try {
            User u = ctx.community().signUpBorrower(bName.getText(), bPhone.getText(), bCategory.getValue(), bMarket.getText(),
                    bCity.getText(), bYears.getValue(), bUser.getText(), bPass.getText(), bConfirm.getText());
            onSuccess.accept(u, true);
        } catch (ValidationException ex) {
            bError.setText(ex.getMessage());
            TextInputControl f = fields.get(ex.getField());
            if (f != null) {
                f.getStyleClass().add("field-error");
                f.requestFocus();
            }
        } catch (Exception ex) {
            Ui.error("Could not create the account", ex);
        }
    }

    private void signUpLender() {
        Map<String, TextInputControl> fields = Map.of("name", lName, "organisation", lOrg, "username", lUser,
                "password", lPass, "confirm", lConfirm);
        clearErrors(fields.values().toArray(TextInputControl[]::new));
        lError.setText("");
        try {
            User u = ctx.community().signUpLender(lName.getText(), lOrg.getText(), lType.getValue(), lUser.getText(),
                    lPass.getText(), lConfirm.getText());
            onSuccess.accept(u, true);
        } catch (ValidationException ex) {
            lError.setText(ex.getMessage());
            TextInputControl f = fields.get(ex.getField());
            if (f != null) {
                f.getStyleClass().add("field-error");
                f.requestFocus();
            }
        } catch (Exception ex) {
            Ui.error("Could not register", ex);
        }
    }

    private static void clearErrors(TextInputControl... controls) {
        for (TextInputControl c : controls) c.getStyleClass().remove("field-error");
    }

    public void focusFirst() {
        username.requestFocus();
    }
}
