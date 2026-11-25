package com.trustlend.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.User;
import com.trustlend.service.AppContext;
import com.trustlend.service.CommunityService;
import com.trustlend.service.DemoAccounts;
import com.trustlend.service.LoanService;
import com.trustlend.util.Formats;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A tiny web server (JDK {@code com.sun.net.httpserver}) so a borrower can use TrustLend from their
 * phone on the same Wi-Fi: see their score, confirm or dispute reported repayments, and upload a
 * statement. Every change shows up on the laptop at once.
 *
 * <p>Meant for demos on a local network: plain HTTP, borrower accounts only, sessions kept in
 * memory until the app closes.
 */
public final class PhoneServer {

    private static final Logger LOG = Logger.getLogger(PhoneServer.class.getName());
    private static PhoneServer running;

    private final AppContext ctx;
    private final HttpServer server;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();   // token -> user id
    private final Consumer<String> onChange;
    private final String address;

    private PhoneServer(AppContext ctx, int port, Consumer<String> onChange) throws IOException {
        this.ctx = ctx;
        this.onChange = onChange;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "phone-server");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handle);
        server.start();
        this.address = "http://" + lanAddress() + ":" + port + "/";
        LOG.info("Phone page running at " + address);
    }

    /** Starts the server once; later calls return the running one. */
    public static synchronized PhoneServer start(AppContext ctx, int port, Consumer<String> onChange) throws IOException {
        if (running == null) running = new PhoneServer(ctx, port, onChange);
        return running;
    }

    public static synchronized void stopIfRunning() {
        if (running != null) {
            running.server.stop(0);
            running = null;
            LOG.info("Phone page stopped");
        }
    }

    public static synchronized boolean isRunning() {
        return running != null;
    }

    public String address() {
        return address;
    }

    /** This computer's address on the local network (e.g. 192.168.1.23), preferring home/office Wi-Fi ranges. */
    static String lanAddress() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase();
                if (name.contains("virtual") || name.contains("vmware") || name.contains("vbox") || name.contains("docker")) continue;
                for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) found.add(a.getHostAddress());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not list network interfaces", e);
        }
        return found.stream().filter(a -> a.startsWith("192.168.")).findFirst()
                .or(() -> found.stream().filter(a -> a.startsWith("10.")).findFirst())
                .or(() -> found.stream().findFirst())
                .orElse("localhost");
    }

    // ------------------------------------------------------------------ routing

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = parse(ex.getRequestURI().getRawQuery());
            switch (path) {
                case "/", "/index.html" -> send(ex, 200, "text/html; charset=utf-8", PhonePage.HTML);
                case "/api/login" -> login(ex, parse(body(ex)));
                case "/api/me" -> json(ex, me(user(q)));
                case "/api/confirm" -> {
                    User u = user(q);
                    ctx.community().confirm(u, Long.parseLong(q.get("id")));
                    changed(u.getFullName() + " confirmed a repayment from their phone");
                    json(ex, me(u));
                }
                case "/api/dispute" -> {
                    User u = user(q);
                    ctx.community().dispute(u, Long.parseLong(q.get("id")), q.getOrDefault("reason", ""));
                    changed(u.getFullName() + " disputed a repayment from their phone");
                    json(ex, me(u));
                }
                case "/api/upload" -> {
                    User u = user(q);
                    List<String> lines = CommunityService.lines(body(ex));
                    json(ex, upload(u, q.getOrDefault("name", "statement.csv"), lines));
                }
                case "/api/sample" -> {
                    User u = user(q);
                    boolean bad = "bad".equals(q.get("kind"));
                    LocalDate today = LocalDate.now();
                    List<String> lines = bad ? DemoAccounts.statement(today.minusDays(27), 28, System.nanoTime() % 997, true)
                            : DemoAccounts.statement(today.minusDays(84), 84, System.nanoTime() % 997, false);
                    json(ex, upload(u, bad ? "suspicious_sample.csv" : "phonepe_sample.csv", lines));
                }
                default -> send(ex, 404, "text/plain", "Not found");
            }
        } catch (SecurityException e) {
            send(ex, 401, "application/json", "{\"error\":" + str(e.getMessage()) + "}");
        } catch (Exception e) {
            send(ex, 400, "application/json", "{\"error\":" + str(e.getMessage() == null ? e.toString() : e.getMessage()) + "}");
        }
    }

    private void login(HttpExchange ex, Map<String, String> form) throws Exception {
        User u = ctx.auth().authenticate(form.get("username"), form.get("password"), null);
        if (!u.isBorrower()) throw new SecurityException("The phone page is for borrowers. Lenders and HQ use the desktop app.");
        String token = UUID.randomUUID().toString();
        sessions.put(token, u.getId());
        changed(u.getFullName() + " opened TrustLend on their phone");
        json(ex, "{\"token\":" + str(token) + "}");
    }

    private User user(Map<String, String> q) throws Exception {
        Long id = sessions.get(q.getOrDefault("token", ""));
        if (id == null) throw new SecurityException("Please log in again");
        return ctx.community().members().stream().filter(u -> u.getId() == id).findFirst()
                .orElseThrow(() -> new SecurityException("Account no longer exists"));
    }

    private void changed(String message) {
        if (onChange != null) onChange.accept(message);
    }

    // ------------------------------------------------------------------ responses

    private String me(User u) throws Exception {
        long vid = u.getVendorId();
        ScoreBreakdown s = ctx.scores().scoreOf(vid).orElse(null);
        LoanService.Offer offer = ctx.loans().offerFor(vid);
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":").append(str(u.getFullName())).append(',');
        sb.append("\"id\":").append(str(CommunityService.trustId(vid))).append(',');
        if (s != null) {
            sb.append("\"score\":").append(s.score()).append(',');
            sb.append("\"band\":").append(str(s.band().getLabel())).append(',');
            sb.append("\"color\":").append(str(s.band().getColorHex())).append(',');
            sb.append("\"factors\":[")
                    .append(factor("Regular sales", s.consistency())).append(',')
                    .append(factor("Repaying on time", s.repayment())).append(',')
                    .append(factor("Tenure & income", s.tenure())).append(',')
                    .append(factor("Community trust", s.network())).append("],");
        }
        sb.append("\"offer\":").append(str(offer.maxAmount() > 0
                ? "You can borrow up to " + Formats.inr(offer.maxAmount()) + " at " + String.format("%.1f%%", offer.interestRate()) + " a year"
                : offer.note())).append(',');
        List<String> claims = new ArrayList<>();
        for (CommunityService.Claim c : ctx.community().pendingClaims(vid)) {
            String text = c.loan().getOrigin() == com.trustlend.model.Loan.Origin.REPORTED
                    ? "Says you borrowed " + Formats.inr(c.loan().getPrincipal()) + " and repaid " + Formats.inr(c.repayment().getAmount())
                    + " on " + Formats.date(c.repayment().getPaidDate())
                    : "Says you paid " + Formats.inr(c.repayment().getAmount()) + " on " + Formats.date(c.repayment().getPaidDate());
            claims.add("{\"id\":" + c.repayment().getId() + ",\"lender\":" + str(c.lenderName()) + ",\"text\":" + str(text)
                    + ",\"onTime\":" + c.repayment().isOnTime() + "}");
        }
        sb.append("\"claims\":[").append(String.join(",", claims)).append("],");
        CommunityService.Evidence e = ctx.community().evidence(vid);
        List<String> ev = new ArrayList<>();
        if (e.uploadedSaleDays() > 0) ev.add(str(e.uploadedSaleDays() + " days of sales from your statements"));
        if (e.confirmedRepayments() > 0) ev.add(str(e.confirmedRepayments() + " repayments confirmed"));
        if (e.pendingRepayments() > 0) ev.add(str(e.pendingRepayments() + (e.pendingRepayments() == 1 ? " repayment" : " repayments") + " waiting for you (counts half)"));
        if (e.vouches() > 0) ev.add(str(e.vouches() + " vouches from the community"));
        if (e.verifiedIncome() > 0) ev.add(str("Verified income " + Formats.inr(e.verifiedIncome()) + "/month"));
        else if (e.declaredIncome() > 0) ev.add(str("Declared income " + Formats.inr(e.declaredIncome()) + "/month (not verified yet)"));
        sb.append("\"evidence\":[").append(String.join(",", ev)).append("]}");
        return sb.toString();
    }

    private String upload(User u, String name, List<String> lines) throws Exception {
        CommunityService.UploadOutcome o = ctx.community().uploadStatement(u.getVendorId(), name, lines);
        changed(u.getFullName() + " uploaded " + name + " from their phone (" + o.upload().getStatus().getLabel().toLowerCase() + ")");
        List<String> issues = new ArrayList<>();
        for (String i : o.upload().getIssues().split("\n")) if (!i.isBlank()) issues.add(str(i));
        List<String> good = new ArrayList<>();
        for (String p : o.analysis().positives()) good.add(str(p));
        return "{\"quality\":" + o.upload().getQuality() + ",\"status\":" + str(o.upload().getStatus().getLabel())
                + ",\"days\":" + o.daysAdded() + ",\"before\":" + o.scoreBefore() + ",\"after\":" + o.scoreAfter()
                + ",\"issues\":[" + String.join(",", issues) + "],\"positives\":[" + String.join(",", good) + "]}";
    }

    private static String factor(String name, double value) {
        return "{\"name\":" + str(name) + ",\"value\":" + Math.round(value * 100) + "}";
    }

    /** A JSON string literal. */
    static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static void json(HttpExchange ex, String body) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", body);
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(2_000_000);            // statements up to ~2 MB
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> parse(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }
}
