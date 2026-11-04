package com.trustlend;

import com.trustlend.config.AppConfig;
import com.trustlend.config.LoggingSetup;
import com.trustlend.model.User;
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
 *   <li>{@link #init()}  – runs on the launcher thread, before any window: we open the DB here</li>
 *   <li>{@link #start(Stage)} – runs on the JavaFX Application Thread: build Stage → Scene → scene graph</li>
 *   <li>{@link #stop()}  – runs when the last window closes: release resources</li>
 * </ol>
 */
public class TrustLendApp extends Application {

    private static final Logger LOG = Logger.getLogger(TrustLendApp.class.getName());

    private AppContext context;
    private Throwable startupError;
    private Stage stage;

    @Override
    public void init() {
        LoggingSetup.init();
        LOG.info("Starting " + AppConfig.getInstance().get("app.name") + " " + AppConfig.getInstance().get("app.version"));
        try {
            context = AppContext.bootstrap();
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
        LoginView login = new LoginView(context.auth(), this::showMain);
        setRoot(new Scene(login, 1100, 700));
        stage.centerOnScreen();
        login.focusPassword();
    }

    private void showMain(User user) {
        MainWindow main = new MainWindow(context, user, () -> {
            context.auth().logout();
            showLogin();
        });
        setRoot(new Scene(main, 1320, 820));
        stage.centerOnScreen();
        main.toastInfo("Welcome, " + user.getFullName() + ". Scores are up to date.");
    }

    private void setRoot(Scene scene) {
        scene.getStylesheets().add(Ui.stylesheet());
        stage.setScene(scene);
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
        LOG.info("TrustLend stopped");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
