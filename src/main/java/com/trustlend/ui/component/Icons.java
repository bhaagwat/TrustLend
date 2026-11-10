package com.trustlend.ui.component;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.Map;

/**
 * Simple line icons drawn on a 24×24 grid with {@link SVGPath}, so they look the same on every
 * operating system (font symbols such as ⌂ or ⏻ are missing from many Windows fonts).
 * Their colour comes from CSS: {@code .icon { -fx-stroke: ... }}.
 */
public final class Icons {

    private Icons() { }

    private static String circle(double cx, double cy, double r) {
        return "M" + (cx - r) + " " + cy + " a" + r + " " + r + " 0 1 0 " + (2 * r) + " 0 a" + r + " " + r + " 0 1 0 " + (-2 * r) + " 0";
    }

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("dashboard", "M3 3h7v9H3z M14 3h7v5h-7z M14 12h7v9h-7z M3 16h7v5H3z"),
            Map.entry("vendors", circle(9, 8, 3.5) + " M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6 M16 4.8a3.2 3.2 0 0 1 0 6.4 M18 14.3c2.2.6 3.5 2.7 3.5 5.7"),
            Map.entry("network", circle(5.5, 6, 2.5) + " " + circle(18.5, 6, 2.5) + " " + circle(12, 18.5, 2.5)
                    + " M8 6h8 M6.8 8.2l4 8 M17.2 8.2l-4 8"),
            Map.entry("loans", "M3 7.5h18v12H3z M3 11h18 M6.5 4.5h11 M7 15.5h4"),
            Map.entry("transactions", "M4 8h15 M15 4l4 4-4 4 M20 16H5 M9 12l-4 4 4 4"),
            Map.entry("data", "M6 3h9l4 4v14H6z M15 3v4h4 M9.5 17v-3 M12.5 17v-6 M15.5 17v-4"),
            Map.entry("settings", "M4 6h9 M17 6h3 M15 4v4 M4 12h3 M11 12h9 M9 10v4 M4 18h11 M19 18h1 M17 16v4"),
            Map.entry("help", circle(12, 12, 9) + " M9.6 9.4a2.5 2.5 0 1 1 3.4 2.4c-.7.3-1 .8-1 1.5v.7 M12 17v.2"),
            Map.entry("play", circle(12, 12, 9) + " M10 8.5l5.5 3.5-5.5 3.5z"),
            Map.entry("logout", "M14 4h5v16h-5 M10 8l-4 4 4 4 M6 12h10"),
            Map.entry("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"),
            Map.entry("sun", circle(12, 12, 4) + " M12 2v2.5 M12 19.5V22 M2 12h2.5 M19.5 12H22 M4.9 4.9l1.8 1.8 M17.3 17.3l1.8 1.8 M4.9 19.1l1.8-1.8 M17.3 6.7l1.8-1.8"),
            Map.entry("present", "M4 9V4h5 M20 9V4h-5 M4 15v5h5 M20 15v5h-5"),
            Map.entry("award", circle(12, 9, 6) + " M8.5 14l-1.5 7 5-3 5 3-1.5-7"),
            Map.entry("check", "M5 12.5l4.5 4.5L19 7.5"),
            Map.entry("cross", "M6 6l12 12 M18 6L6 18"),
            Map.entry("spark", "M12 3v4 M12 17v4 M3 12h4 M17 12h4 M6 6l2.5 2.5 M15.5 15.5L18 18 M6 18l2.5-2.5 M15.5 8.5L18 6"));

    /** An icon of the given size in pixels, e.g. {@code Icons.of("vendors", 18)}. */
    public static Node of(String name, double size) {
        String d = PATHS.get(name);
        if (d == null) throw new IllegalArgumentException("Unknown icon " + name);
        SVGPath path = new SVGPath();
        path.setContent(d);
        path.getStyleClass().addAll("icon", "icon-" + name);
        path.setFill(null);
        path.setStrokeWidth(1.9);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        Group g = new Group(path);   // a Group's bounds include the scale, so layout sees the real size
        g.getStyleClass().add("icon-box");
        return g;
    }
}
