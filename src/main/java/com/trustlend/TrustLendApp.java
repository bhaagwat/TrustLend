package com.trustlend;

import com.trustlend.config.AppConfig;
import com.trustlend.config.LoggingSetup;
import com.trustlend.model.User;
import com.trustlend.service.AccountManager;
import com.trustlend.service.AppContext;
import com.trustlend.ui.LoginView;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX entry point. Demonstrates the Application life-cycle (Unit 5):
 * <ol>
 *   <li>{@link #init()}  – runs on the launcher thread, before any window: we open the accounts DB here</li>
 *   <li>{@link #start(Stage)} – runs on the JavaFX Application Thread: build Stage → Scene → scene graph</li>
 *   <li>{@link #stop()}  – runs when the last window closes: release resources</li>
 * </ol>
 * After every login the user's own workspace database is opened; it is closed again on logout.
 */
public class TrustLendApp extends Application {

    private static final Logger LOG = Logger.getLogger(TrustLendApp.class.getName());

    private AccountManager accounts;
    private AppContext workspace;
    private Throwable startupError;
    private Stage stage;

    @Override
    public void init() {
        LoggingSetup.init();
        LOG.info("Starting " + AppConfig.getInstance().get("app.name") + " " + AppConfig.getInstance().get("app.version"));
        try {
            accounts = AccountManager.open();
        } catch (Exception e) {
            startupError = e;
            LOG.log(Level.SEVERE, "Startup failed", e);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        Ui.setOwner(primaryStage);
        primaryStage.setTitle("TrustLend — Micro-lending trust scores");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);

        if (startupError != null) {
            Ui.error("TrustLend could not start", startupError);
            Platform.exit();
            return;
        }
        showLogin();
        primaryStage.show();
    }

    private void showLogin() {
        String notice = accounts.legacyMigrated()
                ? "Your data from the previous version is in the account  admin / " + AccountManager.LEGACY_PASSWORD
                : null;
        LoginView login = new LoginView(accounts.auth(), this::openWorkspace, notice);
        setRoot(new Scene(login, 1100, 700));
        stage.centerOnScreen();
        login.focusFirst();
    }

    private void openWorkspace(User user, boolean newAccount) {
        try {
            workspace = accounts.openWorkspace(user);
            boolean empty = workspace.isEmpty();
            MainWindow main = new MainWindow(workspace, user, this::logout);
            setRoot(new Scene(main, 1320, 820));
            stage.centerOnScreen();
            if (newAccount) main.toast("Account created — welcome, " + user.getFullName() + "!");
            else main.toastInfo("Welcome back, " + user.getFullName() + "." + (empty ? "" : " Scores are up to date."));
        } catch (Exception e) {
            closeWorkspace();
            accounts.auth().logout();
            Ui.error("Could not open your workspace", e);
        }
    }

    private void logout() {
        closeWorkspace();
        accounts.auth().logout();
        showLogin();
    }

    private void closeWorkspace() {
        if (workspace != null) {
            workspace.close();
            workspace = null;
        }
    }

    private void setRoot(Scene scene) {
        Ui.style(scene);
        stage.setScene(scene);
    }

    @Override
    public void stop() {
        closeWorkspace();
        if (accounts != null) accounts.close();
        LOG.info("TrustLend stopped");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
