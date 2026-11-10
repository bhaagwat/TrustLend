package com.trustlend.ui.view;

import com.trustlend.graph.TrustGraph;
import com.trustlend.model.RiskBand;
import com.trustlend.model.Vendor;
import com.trustlend.service.AppContext;
import com.trustlend.ui.MainWindow;
import com.trustlend.ui.Ui;
import com.trustlend.ui.component.Avatar;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Interactive visualisation of the weighted vouch graph.
 * <ul>
 *   <li>Force-directed layout (Fruchterman–Reingold), seeded by market so bazaars form clusters</li>
 *   <li>Node size and colour = trust score and band; edge thickness = credibility-adjusted vouch weight</li>
 *   <li>Click a vendor to highlight who vouches for them (saffron) and whom they vouch for (teal)</li>
 *   <li>Drag nodes, scroll to zoom, drag the background to pan</li>
 *   <li>"Strongest trust path" runs Dijkstra on −log(weight) between two vendors</li>
 * </ul>
 */
public class NetworkView extends BaseView {

    private static final double W = 1100;
    private static final double H = 760;
    private static final Color IN_COLOR = Color.web("#f29f05");
    private static final Color OUT_COLOR = Color.web("#0f766e");
    private static final Color EDGE_COLOR = Color.web("#5b6185");

    private final Pane canvas = new Pane();
    private final Group zoomGroup = new Group(canvas);
    private final StackPane viewport = new StackPane();
    private final VBox sidePanel = new VBox(12);
    private final ComboBox<Object> marketFilter = new ComboBox<>();
    private final ComboBox<Vendor> pathFrom = new ComboBox<>();
    private final ComboBox<Vendor> pathTo = new ComboBox<>();
    private final Label pathResult = Ui.label("", "small");
    private final Label stats = Ui.label("", "muted", "small");

    private final Map<Long, NodeView> nodes = new HashMap<>();
    private final List<EdgeView> edges = new ArrayList<>();
    private final Map<Long, Vendor> vendors = new HashMap<>();
    private TrustGraph<Long> graph = new TrustGraph<>();
    private Long selected;
    private Long hovered;
    private int fitAttempts;
    private double dragAnchorX, dragAnchorY, panStartX, panStartY;

    public NetworkView(AppContext ctx, MainWindow shell) {
        super(ctx, shell);
        Button reset = new Button("Reset view");
        reset.getStyleClass().add("btn-ghost");
        reset.setOnAction(e -> {
            clearSelection();
            fitToView();
        });
        Button relayout = new Button("Re-arrange");
        relayout.getStyleClass().add("btn-ghost");
        relayout.setOnAction(e -> {
            layoutNodes(System.nanoTime());
            fitToView();
        });
        setHeader("Trust Network", "Vouches form a weighted, directed graph — trust flows along the arrows", relayout, reset);

        canvas.setPrefSize(W, H);
        canvas.setMinSize(W, H);
        canvas.setMaxSize(W, H);

        viewport.getChildren().add(zoomGroup);
        viewport.getStyleClass().add("graph-canvas");
        viewport.setMinSize(400, 400);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        viewport.setClip(clip);
        installZoomAndPan();

        marketFilter.valueProperty().addListener((o, a, b) -> applyHighlight());
        HBox legend = buildLegend();
        HBox toolbar = Ui.row(12, Ui.label("Highlight market", "form-label"), marketFilter, Ui.hSpacer(), legend);

        VBox left = new VBox(12, toolbar, viewport, stats);
        VBox.setVgrow(viewport, Priority.ALWAYS);
        HBox.setHgrow(left, Priority.ALWAYS);

        sidePanel.setPrefWidth(310);
        sidePanel.setMinWidth(310);
        ScrollPane sideScroll = new ScrollPane(sidePanel);
        sideScroll.setFitToWidth(true);
        sideScroll.setMinWidth(330);
        sideScroll.setPrefWidth(330);

        HBox body = new HBox(16, left, sideScroll);
        setCenter(body);
    }

    private HBox buildLegend() {
        HBox legend = new HBox(10);
        legend.setAlignment(Pos.CENTER_RIGHT);
        for (RiskBand b : RiskBand.values()) {
            Label dot = new Label();
            dot.getStyleClass().add("legend-dot");
            dot.setStyle("-fx-background-color: " + b.getColorHex() + ";");
            legend.getChildren().add(Ui.row(4, dot, Ui.label(b.getLabel(), "small", "muted")));
        }
        return legend;
    }

    private void installZoomAndPan() {
        viewport.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
            double scale = Math.max(0.4, Math.min(3.0, zoomGroup.getScaleX() * factor));
            zoomGroup.setScaleX(scale);
            zoomGroup.setScaleY(scale);
            e.consume();
        });
        viewport.setOnMousePressed(e -> {
            if (e.getTarget() == viewport || e.getTarget() == canvas) {
                panStartX = e.getSceneX() - zoomGroup.getTranslateX();
                panStartY = e.getSceneY() - zoomGroup.getTranslateY();
            }
        });
        viewport.setOnMouseDragged(e -> {
            if (e.getTarget() == viewport || e.getTarget() == canvas) {
                zoomGroup.setTranslateX(e.getSceneX() - panStartX);
                zoomGroup.setTranslateY(e.getSceneY() - panStartY);
            }
        });
        canvas.setOnMouseClicked(e -> {
            if (e.getTarget() == canvas && e.isStillSincePress()) clearSelection();
        });
    }

    // ------------------------------------------------------------------ data

    @Override
    public void refresh() {
        try {
            vendors.clear();
            List<Vendor> list = ctx.vendors().findAll();
            list.forEach(v -> vendors.put(v.getId(), v));
            graph = ctx.scores().graph();

            Object market = marketFilter.getValue();
            Set<String> markets = new TreeSet<>();
            list.forEach(v -> markets.add(v.getMarketArea()));
            marketFilter.getItems().setAll("All markets");
            marketFilter.getItems().addAll(markets);
            marketFilter.setValue(market != null && marketFilter.getItems().contains(market) ? market : "All markets");

            List<Vendor> sortedVendors = new ArrayList<>(list);
            sortedVendors.sort(Comparator.comparing(Vendor::getName));
            pathFrom.setItems(FXCollections.observableArrayList(sortedVendors));
            pathTo.setItems(FXCollections.observableArrayList(sortedVendors));

            buildScene();
            layoutNodes(7);
            fitAttempts = 0;
            fitToView();
            int n = graph.nodeCount();
            int m = graph.edgeCount();
            double density = n > 1 ? m / (double) (n * (n - 1)) : 0;
            stats.setText(n + " vendors · " + m + " vouches · density " + String.format("%.3f", density)
                    + " · avg " + String.format("%.1f", n == 0 ? 0 : m / (double) n) + " vouches per vendor"
                    + "  —  scroll to zoom, drag to pan or move vendors");
            if (selected != null && nodes.containsKey(selected)) select(selected);
            else showOverview();
        } catch (Exception e) {
            Ui.error("Could not load trust network", e);
        }
    }

    private void buildScene() {
        canvas.getChildren().clear();
        nodes.clear();
        edges.clear();
        Group edgeLayer = new Group();
        Group nodeLayer = new Group();
        canvas.getChildren().addAll(edgeLayer, nodeLayer);

        for (Long id : graph.nodes()) {
            Vendor v = vendors.get(id);
            if (v == null) continue;
            NodeView nv = new NodeView(v);
            nodes.put(id, nv);
            nodeLayer.getChildren().add(nv);
        }
        for (TrustGraph.Edge<Long> e : graph.edges()) {
            NodeView a = nodes.get(e.from());
            NodeView b = nodes.get(e.to());
            if (a == null || b == null) continue;
            EdgeView ev = new EdgeView(a, b, e.weight());
            edges.add(ev);
            edgeLayer.getChildren().add(ev);
        }
    }

    /**
     * Fruchterman–Reingold force-directed layout: every pair of nodes repels, every edge
     * attracts, and a "temperature" limits movement and cools down each iteration.
     */
    private void layoutNodes(long seed) {
        if (nodes.isEmpty()) return;
        Random random = new Random(seed);
        List<NodeView> list = new ArrayList<>(nodes.values());
        list.sort(Comparator.comparingLong(nv -> nv.vendor.getId()));

        // seed positions: each market gets its own region of the canvas
        List<String> markets = list.stream().map(nv -> nv.vendor.getMarketArea()).distinct().sorted().toList();
        Map<NodeView, double[]> pos = new HashMap<>();
        for (NodeView nv : list) {
            int m = markets.indexOf(nv.vendor.getMarketArea());
            double angle = 2 * Math.PI * m / markets.size();
            double cx = W / 2 + Math.cos(angle) * W * 0.28;
            double cy = H / 2 + Math.sin(angle) * H * 0.28;
            pos.put(nv, new double[]{cx + random.nextGaussian() * 40, cy + random.nextGaussian() * 40});
        }

        double area = W * H;
        double k = Math.sqrt(area / list.size()) * 0.75;
        double temperature = W / 8;
        for (int iter = 0; iter < 300; iter++) {
            Map<NodeView, double[]> disp = new HashMap<>();
            for (NodeView v : list) disp.put(v, new double[2]);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    double[] p = pos.get(list.get(i));
                    double[] q = pos.get(list.get(j));
                    double dx = p[0] - q[0], dy = p[1] - q[1];
                    double dist = Math.max(0.01, Math.hypot(dx, dy));
                    double force = k * k / dist;
                    double fx = dx / dist * force, fy = dy / dist * force;
                    disp.get(list.get(i))[0] += fx;
                    disp.get(list.get(i))[1] += fy;
                    disp.get(list.get(j))[0] -= fx;
                    disp.get(list.get(j))[1] -= fy;
                }
            }
            for (EdgeView e : edges) {
                double[] p = pos.get(e.from);
                double[] q = pos.get(e.to);
                double dx = p[0] - q[0], dy = p[1] - q[1];
                double dist = Math.max(0.01, Math.hypot(dx, dy));
                double force = dist * dist / k * (0.5 + e.weight);
                double fx = dx / dist * force, fy = dy / dist * force;
                disp.get(e.from)[0] -= fx;
                disp.get(e.from)[1] -= fy;
                disp.get(e.to)[0] += fx;
                disp.get(e.to)[1] += fy;
            }
            for (NodeView v : list) {
                double[] d = disp.get(v);
                double[] p = pos.get(v);
                // gentle gravity towards the centre keeps isolated vendors on screen
                d[0] += (W / 2 - p[0]) * 0.02 * k / 10;
                d[1] += (H / 2 - p[1]) * 0.02 * k / 10;
                double len = Math.max(0.01, Math.hypot(d[0], d[1]));
                p[0] += d[0] / len * Math.min(len, temperature);
                p[1] += d[1] / len * Math.min(len, temperature);
                p[0] = Math.max(50, Math.min(W - 50, p[0]));
                p[1] = Math.max(40, Math.min(H - 50, p[1]));
            }
            temperature *= 0.97;
        }
        pos.forEach((nv, p) -> nv.moveTo(p[0], p[1]));
    }

    /** Zooms and pans so the whole network fills the visible area (with a margin). */
    private void fitToView() {
        if (nodes.isEmpty()) return;
        double vw = viewport.getWidth();
        double vh = viewport.getHeight();
        if (vw <= 0 || vh <= 0) {
            if (fitAttempts++ < 5) javafx.application.Platform.runLater(this::fitToView);   // not laid out yet
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (NodeView nv : nodes.values()) {
            minX = Math.min(minX, nv.getLayoutX() - nv.radius - 30);
            maxX = Math.max(maxX, nv.getLayoutX() + nv.radius + 30);
            minY = Math.min(minY, nv.getLayoutY() - nv.radius - 10);
            maxY = Math.max(maxY, nv.getLayoutY() + nv.radius + 24);
        }
        double scale = Math.min((vw - 40) / (maxX - minX), (vh - 40) / (maxY - minY));
        scale = Math.max(0.5, Math.min(2.2, scale));
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        // the group scales around the canvas centre and the StackPane centres the canvas in the viewport
        double tx = -(cx - W / 2) * scale;
        double ty = -(cy - H / 2) * scale;
        javafx.animation.Timeline t = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(450),
                new javafx.animation.KeyValue(zoomGroup.scaleXProperty(), scale),
                new javafx.animation.KeyValue(zoomGroup.scaleYProperty(), scale),
                new javafx.animation.KeyValue(zoomGroup.translateXProperty(), tx),
                new javafx.animation.KeyValue(zoomGroup.translateYProperty(), ty)));
        t.play();
    }

    /** Red "shock waves" around the given vendors, used to show how a default spreads. */
    public void pulse(java.util.Collection<Long> vendorIds) {
        javafx.application.Platform.runLater(() -> {
            for (Long id : vendorIds) {
                NodeView nv = nodes.get(id);
                if (nv != null) nv.pulse();
            }
        });
    }

    // ------------------------------------------------------------------ selection & highlight

    private void select(long id) {
        selected = id;
        applyHighlight();
        showDetails(id);
    }

    private void clearSelection() {
        selected = null;
        applyHighlight();
        showOverview();
    }

    public void focusOn(long vendorId) {
        if (nodes.containsKey(vendorId)) select(vendorId);
    }

    private void applyHighlight() {
        Object market = marketFilter.getValue();
        boolean marketMode = market instanceof String s && !s.equals("All markets");
        Long focus = selected != null ? selected : hovered;     // hovering previews a vendor's connections
        for (NodeView nv : nodes.values()) {
            boolean inMarket = !marketMode || nv.vendor.getMarketArea().equals(market);
            boolean related = focus == null || nv.vendor.getId() == focus
                    || graph.weight(focus, nv.vendor.getId()) > 0 || graph.weight(nv.vendor.getId(), focus) > 0;
            nv.setOpacity(inMarket && related ? 1.0 : 0.18);
            nv.setSelected(selected != null && nv.vendor.getId() == selected);
        }
        for (EdgeView e : edges) {
            boolean inMarket = !marketMode || (e.from.vendor.getMarketArea().equals(market)
                    || e.to.vendor.getMarketArea().equals(market));
            if (focus == null) {
                e.style(EDGE_COLOR, inMarket ? 0.45 : 0.06);
            } else if (e.to.vendor.getId() == focus) {
                e.style(IN_COLOR, 0.95);
            } else if (e.from.vendor.getId() == focus) {
                e.style(OUT_COLOR, 0.95);
            } else {
                e.style(EDGE_COLOR, 0.05);
            }
        }
    }

    private void highlightPath(List<Long> path) {
        Set<String> pathEdges = new HashSet<>();
        for (int i = 0; i + 1 < path.size(); i++) pathEdges.add(path.get(i) + ">" + path.get(i + 1));
        Set<Long> pathNodes = new HashSet<>(path);
        for (NodeView nv : nodes.values()) nv.setOpacity(pathNodes.contains(nv.vendor.getId()) ? 1 : 0.15);
        for (EdgeView e : edges) {
            boolean on = pathEdges.contains(e.from.vendor.getId() + ">" + e.to.vendor.getId());
            e.style(on ? Color.web("#8b5cf6") : EDGE_COLOR, on ? 1 : 0.04);
            if (on) e.line.setStrokeWidth(4);
        }
    }

    // ------------------------------------------------------------------ side panel

    private void showOverview() {
        sidePanel.getChildren().clear();
        VBox how = Ui.titledCard("How trust flows",
                wrap("Each arrow is a vouch. Its thickness is the vouch strength (1–5), reduced if the voucher "
                        + "has previously vouched for someone who defaulted."),
                wrap("Scores are computed with personalised PageRank: every vendor starts with trust from their "
                        + "own record, then repeatedly passes a share of it along their vouches. A vouch from a "
                        + "highly trusted vendor therefore counts for more than one from an unknown vendor."),
                wrap("Click any vendor to see their connections."));
        sidePanel.getChildren().addAll(how, pathCard(), topConnectedCard());
    }

    private VBox topConnectedCard() {
        VBox box = Ui.titledCard("Most vouched-for");
        nodes.keySet().stream()
                .sorted(Comparator.comparingInt((Long id) -> graph.inNeighbours(id).size()).reversed())
                .limit(5)
                .forEach(id -> {
                    Vendor v = vendors.get(id);
                    HBox row = Ui.row(8, new Avatar(v.getName(), v.getInitials(), 26), Ui.label(v.getName()),
                            Ui.hSpacer(), Ui.label(graph.inNeighbours(id).size() + " vouches", "chip"));
                    row.setStyle("-fx-cursor: hand;");
                    row.setOnMouseClicked(e -> select(id));
                    box.getChildren().add(row);
                });
        return box;
    }

    private VBox pathCard() {
        pathFrom.setPromptText("From vendor");
        pathTo.setPromptText("To vendor");
        pathFrom.setMaxWidth(Double.MAX_VALUE);
        pathTo.setMaxWidth(Double.MAX_VALUE);
        Button find = new Button("Find strongest trust path");
        find.getStyleClass().add("btn-primary");
        find.setMaxWidth(Double.MAX_VALUE);
        find.setOnAction(e -> findPath());
        pathResult.setWrapText(true);
        return Ui.titledCard("Strongest trust path", wrap("Chain of vouches with the highest combined strength "
                + "(Dijkstra's algorithm on −log weights)."), pathFrom, pathTo, find, pathResult);
    }

    private void findPath() {
        Vendor a = pathFrom.getValue();
        Vendor b = pathTo.getValue();
        if (a == null || b == null) {
            pathResult.setText("Choose both vendors.");
            return;
        }
        Optional<List<Long>> path = graph.strongestPath(a.getId(), b.getId());
        if (path.isEmpty() || path.get().size() < 2) {
            Map<Long, Integer> hops = graph.hopsFrom(a.getId());
            pathResult.setText(a.equals(b) ? "Pick two different vendors."
                    : "No chain of vouches leads from " + a.getName() + " to " + b.getName()
                    + ". (" + (hops.size() - 1) + " vendors are reachable from " + a.getName() + ".)");
            applyHighlight();
            return;
        }
        List<Long> p = path.get();
        double strength = 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.size(); i++) {
            if (i > 0) {
                strength *= Math.min(1, graph.weight(p.get(i - 1), p.get(i)));
                sb.append("  →  ");
            }
            sb.append(vendors.get(p.get(i)).getName());
        }
        pathResult.setText(sb + "\n" + (p.size() - 1) + " hop(s), combined strength " + Math.round(strength * 100) + "%");
        selected = null;
        highlightPath(p);
    }

    private void showDetails(long id) {
        Vendor v = vendors.get(id);
        sidePanel.getChildren().clear();
        Label name = Ui.label(v.getName(), "section-title");
        Label role = Ui.label(v.describeRole(), "muted", "small");
        role.setWrapText(true);
        Label score = Ui.label(String.valueOf(v.getCurrentScore()), "stat-value");
        HBox scoreRow = Ui.row(10, score, Ui.bandPill(v.getRiskBand()));
        Button open = new Button("Open full profile");
        open.getStyleClass().add("btn-primary");
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(e -> shell.openVendor(id));
        Button back = new Button("Show whole network");
        back.getStyleClass().add("btn-ghost");
        back.setMaxWidth(Double.MAX_VALUE);
        back.setOnAction(e -> clearSelection());
        VBox head = Ui.card(Ui.row(10, new Avatar(v.getName(), v.getInitials(), 40), new VBox(2, name, role)),
                scoreRow, open, back);

        VBox in = Ui.titledCard("Vouched for by (" + graph.inNeighbours(id).size() + ")");
        neighbourRows(in, graph.inNeighbours(id), IN_COLOR);
        VBox out = Ui.titledCard("Vouches for (" + graph.outNeighbours(id).size() + ")");
        neighbourRows(out, graph.outNeighbours(id), OUT_COLOR);

        Map<Long, Integer> hops = graph.hopsFrom(id);
        Label reach = wrap("Trust from " + v.getName() + " can reach " + (hops.size() - 1)
                + " vendor(s) within " + hops.values().stream().mapToInt(Integer::intValue).max().orElse(0) + " hop(s).");
        sidePanel.getChildren().addAll(head, in, out, Ui.card(reach), pathCard());
        pathFrom.setValue(v);
    }

    private void neighbourRows(VBox box, Map<Long, Double> neighbours, Color color) {
        if (neighbours.isEmpty()) {
            box.getChildren().add(Ui.label("Nobody yet", "muted", "small"));
            return;
        }
        neighbours.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .forEach(e -> {
                    Vendor n = vendors.get(e.getKey());
                    if (n == null) return;
                    Label w = Ui.label(Math.round(e.getValue() * 100) + "%", "chip");
                    w.setStyle("-fx-text-fill: " + toHex(color) + ";");
                    Tooltip.install(w, new Tooltip("Credibility-adjusted vouch weight"));
                    HBox row = Ui.row(8, new Avatar(n.getName(), n.getInitials(), 24), Ui.label(n.getName()),
                            Ui.hSpacer(), Ui.label(String.valueOf(n.getCurrentScore()), "muted", "small"), w);
                    row.setStyle("-fx-cursor: hand;");
                    row.setOnMouseClicked(ev -> select(n.getId()));
                    box.getChildren().add(row);
                });
    }

    private static Label wrap(String text) {
        Label l = Ui.label(text, "small");
        l.setWrapText(true);
        return l;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    // ------------------------------------------------------------------ scene-graph nodes

    /** A vendor drawn as a coloured circle with initials and a name label. Draggable. */
    private class NodeView extends Group {
        final Vendor vendor;
        final double radius;
        private final Circle circle;
        private final Circle ring;
        private final List<EdgeView> attached = new ArrayList<>();

        NodeView(Vendor v) {
            this.vendor = v;
            this.radius = 11 + Math.max(0, v.getCurrentScore() - 300) / 600.0 * 14;
            Color color = Color.web(v.getRiskBand().getColorHex());
            ring = new Circle(radius + 5, Color.TRANSPARENT);
            ring.setStroke(Color.web("#1c2140"));
            ring.setStrokeWidth(2.5);
            ring.setVisible(false);
            circle = new Circle(radius, color);
            circle.setStroke(Color.WHITE);
            circle.setStrokeWidth(2.5);
            Text initials = new Text(v.getInitials());
            initials.setFont(Font.font(null, FontWeight.BOLD, Math.max(9, radius * 0.7)));
            initials.setFill(Color.WHITE);
            initials.setX(-initials.getLayoutBounds().getWidth() / 2);
            initials.setY(initials.getLayoutBounds().getHeight() / 4);
            initials.setMouseTransparent(true);
            Text name = new Text(v.getName().split(" ")[0]);
            name.getStyleClass().add("graph-node-label");
            name.setFont(Font.font(null, FontWeight.SEMI_BOLD, 12.5));
            name.setX(-name.getLayoutBounds().getWidth() / 2);
            name.setY(radius + 14);
            name.setMouseTransparent(true);
            getChildren().addAll(ring, circle, initials, name);
            Tooltip.install(circle, new Tooltip(v.getName() + "\n" + v.getMarketArea() + "\nScore " + v.getCurrentScore()
                    + " (" + v.getRiskBand().getLabel() + ")"));
            setStyle("-fx-cursor: hand;");

            setOnMousePressed(e -> {
                dragAnchorX = e.getSceneX();
                dragAnchorY = e.getSceneY();
                e.consume();
            });
            setOnMouseDragged(e -> {
                double scale = zoomGroup.getScaleX();
                moveTo(getLayoutX() + (e.getSceneX() - dragAnchorX) / scale,
                        getLayoutY() + (e.getSceneY() - dragAnchorY) / scale);
                dragAnchorX = e.getSceneX();
                dragAnchorY = e.getSceneY();
                e.consume();
            });
            setOnMouseEntered(e -> {
                if (selected == null) {
                    hovered = vendor.getId();
                    applyHighlight();
                }
            });
            setOnMouseExited(e -> {
                if (hovered != null) {
                    hovered = null;
                    applyHighlight();
                }
            });
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) {
                    if (e.getClickCount() == 2) shell.openVendor(vendor.getId());
                    else select(vendor.getId());
                }
                e.consume();
            });
        }

        void moveTo(double x, double y) {
            setLayoutX(x);
            setLayoutY(y);
            attached.forEach(EdgeView::update);
        }

        void setSelected(boolean on) {
            ring.setVisible(on);
        }

        void pulse() {
            for (int i = 0; i < 3; i++) {
                Circle wave = new Circle(radius, Color.TRANSPARENT);
                wave.setStroke(Color.web("#d93025"));
                wave.setStrokeWidth(3);
                wave.setMouseTransparent(true);
                getChildren().add(0, wave);
                javafx.animation.Timeline t = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                                new javafx.animation.KeyValue(wave.radiusProperty(), radius),
                                new javafx.animation.KeyValue(wave.opacityProperty(), 0.9)),
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(1400),
                                new javafx.animation.KeyValue(wave.radiusProperty(), radius + 42),
                                new javafx.animation.KeyValue(wave.opacityProperty(), 0)));
                t.setDelay(javafx.util.Duration.millis(i * 450));
                t.setCycleCount(3);
                t.setOnFinished(e -> getChildren().remove(wave));
                t.play();
            }
            circle.setStroke(Color.web("#d93025"));
            javafx.animation.PauseTransition back = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(6));
            back.setOnFinished(e -> circle.setStroke(Color.WHITE));
            back.play();
        }
    }

    /** A directed edge: a line plus an arrowhead that stops at the target circle's border. */
    private static class EdgeView extends Group {
        final NodeView from;
        final NodeView to;
        final double weight;
        final Line line = new Line();
        final Polygon arrow = new Polygon();
        private final double baseWidth;

        EdgeView(NodeView from, NodeView to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.baseWidth = 0.8 + weight * 2.6;
            line.setStrokeWidth(baseWidth);
            line.setMouseTransparent(true);
            arrow.setMouseTransparent(true);
            getChildren().addAll(line, arrow);
            from.attached.add(this);
            to.attached.add(this);
            setMouseTransparent(true);
        }

        void update() {
            double x1 = from.getLayoutX(), y1 = from.getLayoutY();
            double x2 = to.getLayoutX(), y2 = to.getLayoutY();
            double dx = x2 - x1, dy = y2 - y1;
            double len = Math.max(0.01, Math.hypot(dx, dy));
            double ux = dx / len, uy = dy / len;
            double ex = x2 - ux * (to.radius + 3), ey = y2 - uy * (to.radius + 3);
            line.setStartX(x1 + ux * from.radius);
            line.setStartY(y1 + uy * from.radius);
            line.setEndX(ex - ux * 6);
            line.setEndY(ey - uy * 6);
            double size = 5 + baseWidth;
            arrow.getPoints().setAll(
                    ex, ey,
                    ex - ux * size * 1.6 - uy * size * 0.8, ey - uy * size * 1.6 + ux * size * 0.8,
                    ex - ux * size * 1.6 + uy * size * 0.8, ey - uy * size * 1.6 - ux * size * 0.8);
        }

        void style(Color color, double opacity) {
            line.setStroke(color);
            arrow.setFill(color);
            line.setStrokeWidth(baseWidth);
            setOpacity(opacity);
        }
    }
}
