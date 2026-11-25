package com.trustlend.ui;

import com.trustlend.model.User;
import com.trustlend.service.AppContext;
import com.trustlend.ui.component.Avatar;
import com.trustlend.ui.component.GuidedTour;
import com.trustlend.ui.component.Icons;
import com.trustlend.service.StoryScenario;
import com.trustlend.ui.component.Toast;
import com.trustlend.ui.view.BaseView;
import com.trustlend.ui.view.BorrowerHomeView;
import com.trustlend.ui.view.ConfirmationsView;
import com.trustlend.ui.view.UploadView;
import com.trustlend.ui.view.MyLoansView;
import com.trustlend.ui.view.LenderHomeView;
import com.trustlend.ui.view.VerificationView;
import com.trustlend.ui.view.MembersView;
import com.trustlend.ui.view.DashboardView;
import com.trustlend.ui.view.DataView;
import com.trustlend.ui.view.LoansView;
import com.trustlend.ui.view.NetworkView;
import com.trustlend.ui.view.SettingsView;
import com.trustlend.ui.view.TransactionsView;
import com.trustlend.ui.view.VendorProfileView;
import com.trustlend.ui.view.VendorsView;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The application shell: sidebar navigation on the left, the active view in the centre,
 * and an overlay layer (StackPane) for toasts and the busy indicator.
 */
public class MainWindow extends StackPane {

    /** Navigation targets. */
    public enum Page {
        // borrower
        MY_SCORE("My TrustLend", "award"),
        CONFIRM("Confirmations", "check"),
        UPLOAD("Upload proof", "upload"),
        MY_LOANS("My loans", "loans"),
        // lender
        LENDER_HOME("Lender desk", "dashboard"),
        // HQ (and shared)
        DASHBOARD("Dashboard", "dashboard"),
        VENDORS("Borrowers", "vendors"),
        NETWORK("Trust Network", "network"),
        LOANS("Loans", "loans"),
        TRANSACTIONS("Transactions", "transactions"),
        VERIFY("Verification", "shield"),
        MEMBERS("Members", "vendors"),
        DATA("Reports & Data", "data"),
        SETTINGS("Settings", "settings");

        final String title;
        final String icon;

        Page(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private final AppContext ctx;
    private final User user;
    private final Page home;
    private final Runnable onLogout;
    private final BorderPane frame = new BorderPane();
    private final Map<Page, BaseView> views = new EnumMap<>(Page.class);
    private final Map<Page, ToggleButton> navButtons = new EnumMap<>(Page.class);
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane busyOverlay = new StackPane();
    private final Label busyLabel = new Label();
    private GuidedTour tour;

    public MainWindow(AppContext ctx, User user, Runnable onLogout) {
        this.ctx = ctx;
        this.user = user;
        this.home = user.isBorrower() ? Page.MY_SCORE : user.isLender() ? Page.LENDER_HOME : Page.DASHBOARD;
        this.onLogout = onLogout;
        frame.setLeft(buildSidebar(user));
        getChildren().add(frame);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(56, 56);
        VBox busyBox = new VBox(14, spinner, busyLabel);
        busyBox.setAlignment(Pos.CENTER);
        busyOverlay.getChildren().add(busyBox);
        busyOverlay.getStyleClass().add("busy-overlay");
        busyOverlay.setVisible(false);
        getChildren().add(busyOverlay);

        navigate(home);
        updateBadges();
    }

    /** The logged-in user (borrower, lender or HQ): views use it to show the right actions. */
    public User user() {
        return user;
    }

    public Page home() {
        return home;
    }

    private VBox buildSidebar(User user) {
        StackPane mark = new StackPane(new Label("T"));
        mark.getStyleClass().add("brand-mark");
        VBox brandText = new VBox(0, Ui.label("TrustLend", "brand-title"), Ui.label("Community credit scores", "brand-sub"));
        HBox brand = new HBox(10, mark, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox nav = new VBox(3);
        if (user.isBorrower()) {
            nav.getChildren().add(Ui.label("MY ACCOUNT", "nav-section"));
            addNav(nav, Page.MY_SCORE, "My TrustLend");
            addNav(nav, Page.CONFIRM, "Confirmations");
            addNav(nav, Page.UPLOAD, "Upload proof");
            addNav(nav, Page.MY_LOANS, "My loans");
            nav.getChildren().add(Ui.label("COMMUNITY", "nav-section"));
            addNav(nav, Page.NETWORK, "Trust Network");
        } else if (user.isLender()) {
            nav.getChildren().add(Ui.label("LENDING", "nav-section"));
            addNav(nav, Page.LENDER_HOME, "Lender desk");
            addNav(nav, Page.VENDORS, "Find a borrower");
            addNav(nav, Page.LOANS, "My loans");
            nav.getChildren().add(Ui.label("COMMUNITY", "nav-section"));
            addNav(nav, Page.NETWORK, "Trust Network");
        } else {
            nav.getChildren().add(Ui.label("OVERVIEW", "nav-section"));
            addNav(nav, Page.DASHBOARD, "Dashboard");
            addNav(nav, Page.VENDORS, "Borrowers");
            addNav(nav, Page.NETWORK, "Trust Network");
            nav.getChildren().add(Ui.label("LENDING", "nav-section"));
            addNav(nav, Page.LOANS, "All loans");
            addNav(nav, Page.TRANSACTIONS, "Transactions");
            nav.getChildren().add(Ui.label("HQ", "nav-section"));
            addNav(nav, Page.VERIFY, "Verification");
            addNav(nav, Page.MEMBERS, "Members");
            addNav(nav, Page.DATA, "Reports & Data");
            addNav(nav, Page.SETTINGS, "Settings");
        }

        // keep one item always selected
        navGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        Avatar avatar = new Avatar(user.getFullName(), initials(user.getFullName()), 34);
        VBox who = new VBox(1, Ui.label(user.getFullName(), "user-name"),
                Ui.label(user.isBorrower() ? "Borrower · " + com.trustlend.service.CommunityService.trustId(user.getVendorId())
                        : user.isLender() ? user.getOrganisation() : "TrustLend HQ", "user-role"));
        Button logout = new Button(null, Icons.of("logout", 18));
        logout.getStyleClass().addAll("nav-button", "icon-button");
        logout.setTooltip(new Tooltip("Log out"));
        logout.setMaxWidth(Button.USE_PREF_SIZE);
        logout.setOnAction(e -> {
            if (Ui.confirm("Log out", "Do you want to log out of TrustLend?", "Log out")) onLogout.run();
        });
        HBox userBox = new HBox(10, avatar, who, Ui.hSpacer(), logout);
        userBox.getStyleClass().add("user-box");

        Button storyButton = new Button("Story mode: Meet Sunil", Icons.of("play", 18));
        storyButton.getStyleClass().add("story-button");
        storyButton.setMaxWidth(Double.MAX_VALUE);
        storyButton.setOnAction(e -> startStory());

        Button tourButton = new Button("Take the tour", Icons.of("help", 18));
        tourButton.getStyleClass().add("tour-button");
        tourButton.setMaxWidth(Double.MAX_VALUE);
        tourButton.setOnAction(e -> startTour());

        ToggleButton darkToggle = new ToggleButton(null, Icons.of("moon", 17));
        darkToggle.getStyleClass().addAll("nav-button", "icon-button");
        darkToggle.setSelected(Ui.isDark());
        darkToggle.setTooltip(new Tooltip("Dark mode"));
        darkToggle.setOnAction(e -> {
            Ui.setDark(darkToggle.isSelected());
            refreshAll();
        });
        ToggleButton presentToggle = new ToggleButton(null, Icons.of("present", 17));
        presentToggle.getStyleClass().addAll("nav-button", "icon-button");
        presentToggle.setSelected(Ui.isPresentation());
        presentToggle.setTooltip(new Tooltip("Presentation mode: larger text for projectors"));
        presentToggle.setOnAction(e -> Ui.setPresentation(presentToggle.isSelected()));
        HBox toggles = new HBox(6, Ui.label("VIEW", "nav-section"), Ui.hSpacer(), darkToggle, presentToggle);
        toggles.setAlignment(Pos.CENTER_LEFT);

        Button phoneButton = new Button("Open on a phone", Icons.of("phone", 18));
        phoneButton.getStyleClass().add("tour-button");
        phoneButton.setMaxWidth(Double.MAX_VALUE);
        phoneButton.setOnAction(e -> PhoneAccess.show(ctx, this));

        VBox demo = new VBox(8, storyButton, tourButton, phoneButton, toggles);
        demo.setPadding(new javafx.geometry.Insets(0, 0, 10, 0));
        VBox sidebar = new VBox(brand, nav, Ui.vSpacer(), demo, userBox);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private void addNav(VBox nav, Page page, String title) {
        ToggleButton b = new ToggleButton(title, Icons.of(page.icon, 18));
        b.setUserData(title);
        b.getStyleClass().add("nav-button");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setToggleGroup(navGroup);
        b.setOnAction(e -> navigate(page));
        navButtons.put(page, b);
        nav.getChildren().add(b);
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String p : name.split("\\s+")) if (!p.isEmpty() && sb.length() < 2) sb.append(p.charAt(0));
        return sb.toString().toUpperCase();
    }

    // ------------------------------------------------------------------ navigation

    /** Views are created lazily the first time they are opened, then cached. */
    public void navigate(Page page) {
        if (!navButtons.containsKey(page)) page = home;     // not available for this kind of user
        BaseView view = views.computeIfAbsent(page, this::createView);
        navButtons.get(page).setSelected(true);
        show(view);
    }

    /** Shows "(2)" next to Confirmations / Verification when something is waiting. */
    public void updateBadges() {
        try {
            if (navButtons.containsKey(Page.CONFIRM)) {
                badge(Page.CONFIRM, ctx.community().pendingClaims(user.getVendorId()).size());
            }
            if (navButtons.containsKey(Page.VERIFY)) {
                badge(Page.VERIFY, ctx.community().queue().size());
            }
        } catch (Exception ignored) {
            // badges are a convenience
        }
    }

    private void badge(Page page, int count) {
        ToggleButton b = navButtons.get(page);
        String title = (String) b.getUserData();
        b.setText(count > 0 ? title + "  (" + count + ")" : title);
        b.getStyleClass().remove("nav-badged");
        if (count > 0) b.getStyleClass().add("nav-badged");
    }

    private BaseView createView(Page page) {
        return switch (page) {
            case MY_SCORE -> new BorrowerHomeView(ctx, this);
            case CONFIRM -> new ConfirmationsView(ctx, this);
            case UPLOAD -> new UploadView(ctx, this);
            case MY_LOANS -> new MyLoansView(ctx, this);
            case LENDER_HOME -> new LenderHomeView(ctx, this);
            case VERIFY -> new VerificationView(ctx, this);
            case MEMBERS -> new MembersView(ctx, this);
            case DASHBOARD -> new DashboardView(ctx, this);
            case VENDORS -> new VendorsView(ctx, this);
            case NETWORK -> new NetworkView(ctx, this);
            case LOANS -> new LoansView(ctx, this);
            case TRANSACTIONS -> new TransactionsView(ctx, this);
            case DATA -> new DataView(ctx, this);
            case SETTINGS -> new SettingsView(ctx, this);
        };
    }

    public void openVendor(long vendorId) {
        if (user.isBorrower()) {                    // borrowers only ever see their own profile
            if (vendorId == user.getVendorId()) navigate(Page.MY_SCORE);
            else toastInfo("Other borrowers' profiles are private");
            return;
        }
        navButtons.get(Page.VENDORS).setSelected(true);
        show(new VendorProfileView(ctx, this, vendorId));
    }

    public void openNetworkAround(long vendorId) {
        navigate(Page.NETWORK);
        ((NetworkView) views.get(Page.NETWORK)).focusOn(vendorId);
    }

    private void show(BaseView view) {
        view.refresh();
        Node current = frame.getCenter();
        frame.setCenter(view);
        if (current != view) {
            FadeTransition ft = new FadeTransition(Duration.millis(180), view);
            ft.setFromValue(0.35);
            ft.setToValue(1);
            ft.play();
        }
    }

    /** Refresh every cached view (after data changes elsewhere). */
    public void refreshAll() {
        updateBadges();
        views.values().forEach(BaseView::refresh);
        if (frame.getCenter() instanceof BaseView v && !views.containsValue(v)) {
            v.refresh();
        }
    }

    /** Marks one sidebar entry for the guided tour (null clears the mark). */
    public void highlightNav(Page page) {
        navButtons.forEach((p, b) -> {
            b.getStyleClass().remove("tour-target");
            if (p == page) b.getStyleClass().add("tour-target");
        });
    }

    // ------------------------------------------------------------------ guided demo

    /** Opens the step-by-step tour card (bottom-right); the app stays fully usable underneath. */
    public void startTour() {
        if (tour != null) getChildren().remove(tour);
        tour = new GuidedTour(this, user.isBorrower() ? GuidedTour.BORROWER_STEPS
                : user.isLender() ? GuidedTour.LENDER_STEPS : GuidedTour.HQ_STEPS, () -> {
            getChildren().remove(tour);
            tour = null;
        });
        getChildren().add(getChildren().indexOf(busyOverlay), tour);   // below the busy overlay
        tour.start();
    }

    /** Opens the full-window "Meet Sunil" story (works in any workspace, nothing is saved). */
    public void startStory() {
        runBusy("Preparing the story…", () -> StoryScenario.build(ctx.scores().getSettings(), LocalDate.now(),
                com.trustlend.config.AppConfig.getInstance().getInt("loan.min.score"), ctx.loans()::interestFor), chapters -> {
            if (tour != null) {
                getChildren().remove(tour);
                tour = null;
                highlightNav(null);
            }
            StoryMode[] holder = new StoryMode[1];
            holder[0] = new StoryMode(chapters, () -> getChildren().remove(holder[0]));
            getChildren().add(getChildren().indexOf(busyOverlay), holder[0]);
            holder[0].start();
        });
    }

    /** HQ: wipes the community (all borrowers, lenders and their data) and loads the demo community again. */
    public void resetCommunity() {
        if (!Ui.confirm("Reset to demo data", "Delete ALL borrowers, lenders, loans, repayments, uploads and vouches, "
                + "and load the demo community again? HQ accounts are kept. This cannot be undone.", "Reset everything")) return;
        runBusy("Rebuilding the demo community…", () -> {
            ctx.resetToDemo();
            return null;
        }, ignored -> {
            navigate(Page.DASHBOARD);
            refreshAll();
            toast("Demo community restored: 36 borrowers, 3 lenders, demo logins ready");
        });
    }

    // ------------------------------------------------------------------ feedback

    public void toast(String message) {
        Toast.show(this, message, Toast.Kind.SUCCESS);
    }

    public void toastError(String message) {
        Toast.show(this, message, Toast.Kind.ERROR);
    }

    public void toastInfo(String message) {
        Toast.show(this, message, Toast.Kind.INFO);
    }

    /** Runs work in the background behind a modal overlay so the UI stays responsive. */
    public <T> void runBusy(String message, Callable<T> work, Consumer<T> onDone) {
        busyLabel.setText(message);
        busyOverlay.setVisible(true);
        Ui.async(work, result -> {
            busyOverlay.setVisible(false);
            onDone.accept(result);
        }, error -> {
            busyOverlay.setVisible(false);
            Ui.error("Something went wrong", error);
        });
    }

    /** Recalculates all scores in the background, then refreshes the screens. */
    public void recalculateScores(Supplier<String> successMessage) {
        recalculateScores(successMessage, null);
    }

    /** As above; {@code after} runs once the new scores are in place. */
    public void recalculateScores(Supplier<String> successMessage, Runnable after) {
        runBusy("Propagating trust through the network…", () -> {
            ctx.loans().refreshOverdue(LocalDate.now());
            return ctx.scores().recalculateAll(LocalDate.now());
        }, scores -> {
            refreshAll();
            toast(successMessage.get());
            if (after != null) after.run();
        });
    }

    /** Opens the trust network with one market highlighted (from the dashboard heat map). */
    public void showMarket(String market) {
        navigate(Page.NETWORK);
        ((NetworkView) views.get(Page.NETWORK)).highlightMarket(market);
    }

    /** Opens the network centred on {@code vendorId} and pulses the given vendors red (default ripple). */
    public void showRippleOnNetwork(long vendorId, java.util.Collection<Long> affected) {
        openNetworkAround(vendorId);
        ((NetworkView) views.get(Page.NETWORK)).pulse(affected);
    }
}
