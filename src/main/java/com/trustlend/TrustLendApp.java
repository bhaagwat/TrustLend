package com.trustlend;

import com.trustlend.config.AppConfig;
import com.trustlend.config.LoggingSetup;
import com.trustlend.model.User;
import com.trustlend.service.AppContext;
import com.trustlend.ui.LoginView;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.SplashView;
import com.trustlend.ui.Ui;
import com.trustlend.web.PhoneServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX entry point. Demonstrates the Application life-cycle (Unit 5):
 * <ol>
 *   <li>{@link #init()}  – runs on the launcher thread, before any window: we open the community database
 *       (the first start fills it with the demo community and demo accounts)</li>
 *   <li>{@link #start(Stage)} – runs on the JavaFX Application Thread: splash screen, then the login page</li>
 *   <li>{@link #stop()}  – runs when the last window closes: stop the phone page, close the database</li>
 * </ol>
 */
public class TrustLendApp extends Application {

    private static final Logger LOG = Logger.getLogger(TrustLendApp.class.getName());

    private AppContext ctx;
    private Throwable startupError;
    private Stage stage;

    @Override
    public void init() {
        LoggingSetup.init();
        LOG.info("Starting " + AppConfig.getInstance().get("app.name") + " " + AppConfig.getInstance().get("app.version"));
        try {
            ctx = AppContext.open();
        } catch (Exception e) {
            startupError = e;
            LOG.log(Level.SEVERE, "Startup failed", e);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        Ui.setOwner(primaryStage);
        primaryStage.setTitle("TrustLend — community credit scores");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);

        if (startupError != null) {
            Ui.error("TrustLend could not start", startupError);
            Platform.exit();
            return;
        }
        SplashView splash = new SplashView();
        setRoot(new Scene(splash, 1100, 700));
        primaryStage.show();
        splash.play(this::showLogin);
    }

    private void showLogin() {
        LoginView login = new LoginView(ctx, this::openHome);
        setRoot(new Scene(login, 1100, 720));
        stage.centerOnScreen();
        login.focusFirst();
    }

    private void openHome(User user, boolean newAccount) {
        try {
            MainWindow main = new MainWindow(ctx, user, this::logout);
            setRoot(new Scene(main, 1320, 820));
            stage.centerOnScreen();
            String first = user.getFullName().split("\\s+")[0];
            if (newAccount) main.toast("Account created — welcome, " + first + "!");
            else main.toastInfo("Welcome back, " + (user.isLender() ? user.getDisplayName() : first) + ".");
        } catch (Exception e) {
            ctx.auth().logout();
            Ui.error("Could not open TrustLend", e);
        }
    }

    private void logout() {
        ctx.auth().logout();
        showLogin();
    }

    private void setRoot(Scene scene) {
        Ui.style(scene);
        stage.setScene(scene);
    }

    @Override
    public void stop() {
        PhoneServer.stopIfRunning();
        if (ctx != null) ctx.close();
        LOG.info("TrustLend stopped");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
