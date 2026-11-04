package com.trustlend.ui.view;

import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Abstract base for every screen: a BorderPane whose top is a page header.
 * Sub-classes build their centre content and implement {@link #refresh()}.
 */
public abstract class BaseView extends BorderPane {

    protected final AppContext ctx;
    protected final MainWindow shell;

    protected BaseView(AppContext ctx, MainWindow shell) {
        this.ctx = ctx;
        this.shell = shell;
        getStyleClass().add("page");
    }

    /** Builds the title row; extra nodes (buttons, search box) go to the right. */
    protected void setHeader(String title, String subtitle, Node... actions) {
        Label t = Ui.label(title, "page-title");
        Label s = Ui.label(subtitle, "page-subtitle");
        VBox text = new VBox(2, t, s);
        HBox header = new HBox(12, text, Ui.hSpacer());
        header.getChildren().addAll(actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 20, 0));
        setTop(header);
    }

    /** Reloads data from the services. Called every time the view is shown. */
    public abstract void refresh();
}
