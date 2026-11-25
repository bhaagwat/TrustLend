package com.trustlend.io;

import com.trustlend.model.Upload;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads a UPI / bank / sales statement exported as CSV (PhonePe, Google Pay, Paytm or a bank
 * statement) and checks it for signs of tampering before the sales count towards a score.
 *
 * <p>Columns are found by their header names, so most exports work as they are: a date column,
 * an amount (or separate credit/debit columns), optionally a Cr/Dr type, a transaction/UTR id
 * and a status. Only money received (credits) counts as sales; failed payments are skipped.
 *
 * <p>Checks: too few rows, duplicate transaction ids, future dates, unreadable rows, too many
 * round amounts, sudden spikes, the same amount repeated, and a very short period. Each costs
 * points from 100; 60+ is accepted, 35-59 is held for HQ review, below 35 is rejected.
 */
public final class StatementImporter {

    /** Sales on one day: total received and number of payments. */
    public record DailySale(LocalDate date, double amount, int payments) { }

    public record Result(List<DailySale> days, int rows, int credits, int quality, Upload.Status status,
                         List<String> issues, List<String> positives) {
        public double totalSales() {
            return days.stream().mapToDouble(DailySale::amount).sum();
        }
    }

    public static final int ACCEPT_AT = 60;
    public static final int REVIEW_AT = 35;

    private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"), DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH), DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH), DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    private StatementImporter() { }

    public static Result analyse(Path file, LocalDate today) throws IOException {
        List<String> lines;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            lines = r.lines().filter(l -> !l.isBlank()).toList();
        }
        return analyse(lines, today);
    }

    /** Same, for text already in memory (e.g. a file sent from the phone page). */
    public static Result analyse(List<String> lines, LocalDate today) throws IOException {
        if (lines.isEmpty()) throw new IOException("The file is empty");
        String first = lines.get(0).startsWith("﻿") ? lines.get(0).substring(1) : lines.get(0);
        String[] header = CsvUtil.split(first).toArray(String[]::new);
        int date = find(header, "date", "time");
        int amount = find(header, "amount", "amt", "value");
        int credit = find(header, "credit", "received", "deposit", "cr amount");
        int debit = find(header, "debit", "paid", "withdrawal", "dr amount");
        int type = find(header, "type", "cr/dr", "dr/cr", "direction");
        int id = find(header, "utr", "transaction id", "txn id", "reference", "ref", "upi ref");
        int status = find(header, "status");
        if (date < 0) throw new IOException("No date column found. Expected a header such as \"Date\".");
        if (amount < 0 && credit < 0) throw new IOException("No amount column found. Expected \"Amount\" or \"Credit\".");
        if (credit == amount) credit = -1;

        int rows = 0, unreadable = 0, credits = 0, future = 0, duplicates = 0, round = 0;
        Set<String> ids = new HashSet<>();
        Map<Double, Integer> sameAmount = new HashMap<>();
        TreeMap<LocalDate, double[]> byDay = new TreeMap<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cols = CsvUtil.split(lines.get(i));
            rows++;
            try {
                if (status >= 0 && at(cols, status).toLowerCase().matches(".*(fail|declin|revers|pending).*")) continue;
                LocalDate d = parseDate(at(cols, date));
                double value;
                if (credit >= 0) {
                    value = money(at(cols, credit));
                    if (value <= 0) continue;                 // a debit row
                } else {
                    value = money(at(cols, amount));
                    String t = type >= 0 ? at(cols, type).toLowerCase() : "";
                    boolean outgoing = t.startsWith("dr") || t.contains("debit") || t.contains("paid") || t.contains("sent")
                            || (debit >= 0 && money(at(cols, debit)) > 0) || value < 0;
                    if (outgoing) continue;
                }
                if (value <= 0) continue;
                credits++;
                if (d.isAfter(today)) future++;
                if (id >= 0) {
                    String key = at(cols, id).trim();
                    if (!key.isEmpty() && !ids.add(key)) duplicates++;
                }
                if (value >= 500 && value % 100 == 0) round++;
                sameAmount.merge(value, 1, Integer::sum);
                double[] day = byDay.computeIfAbsent(d, k -> new double[2]);
                day[0] += value;
                day[1]++;
            } catch (RuntimeException e) {
                unreadable++;
            }
        }

        List<DailySale> days = new ArrayList<>();
        byDay.forEach((d, v) -> days.add(new DailySale(d, Math.round(v[0] * 100) / 100.0, (int) v[1])));

        // ---- checks
        List<String> issues = new ArrayList<>();
        int penalty = 0;
        if (credits < 10) {
            penalty += 30;
            issues.add("Only " + credits + " incoming payments — too few to judge a business");
        }
        if (id < 0) {
            penalty += 10;
            issues.add("No transaction / UTR id column, so duplicate rows cannot be detected");
        } else if (duplicates > 0) {
            penalty += Math.min(40, duplicates * 10);
            issues.add(duplicates + " duplicate transaction id(s) — the same payment appears more than once");
        }
        if (future > 0) {
            penalty += 30;
            issues.add(future + " payment(s) dated in the future");
        }
        if (rows > 0 && unreadable > rows * 0.1) {
            penalty += 10;
            issues.add(unreadable + " row(s) could not be read");
        }
        if (credits >= 10 && round > credits * 0.6) {
            penalty += 25;
            issues.add(Math.round(100.0 * round / credits) + "% of payments are round hundreds — real UPI sales rarely are");
        }
        if (credits >= 10) {
            int top = sameAmount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (top > credits * 0.4) {
                penalty += 20;
                issues.add("The same amount repeats in " + Math.round(100.0 * top / credits) + "% of payments");
            }
        }
        if (days.size() >= 5) {
            double[] totals = days.stream().mapToDouble(DailySale::amount).sorted().toArray();
            double median = totals[totals.length / 2];
            long spikes = days.stream().filter(d -> d.amount() > 6 * median).count();
            if (spikes > 0) {
                penalty += (int) Math.min(30, spikes * 15);
                issues.add(spikes + " day(s) with sales over 6× a normal day — unusual spike");
            }
        }
        if (!days.isEmpty() && days.get(days.size() - 1).date().toEpochDay() - days.get(0).date().toEpochDay() < 7) {
            penalty += 10;
            issues.add("Covers less than a week");
        }

        int quality = Math.max(0, Math.min(100, 100 - penalty));
        Upload.Status verdict = quality >= ACCEPT_AT ? Upload.Status.ACCEPTED
                : quality >= REVIEW_AT ? Upload.Status.REVIEW : Upload.Status.REJECTED;

        List<String> positives = new ArrayList<>();
        if (!days.isEmpty()) {
            positives.add(credits + " payments received on " + days.size() + " different days");
            positives.add("From " + days.get(0).date() + " to " + days.get(days.size() - 1).date());
            if (id >= 0 && duplicates == 0) positives.add("Every transaction id is unique");
        }
        return new Result(days, rows, credits, quality, verdict, issues, positives);
    }

    // ------------------------------------------------------------------ helpers

    private static int find(String[] header, String... names) {
        for (String n : names) {
            for (int i = 0; i < header.length; i++) {
                String h = header[i].replace("\"", "").trim().toLowerCase();
                if (h.equals(n)) return i;
            }
        }
        for (String n : names) {
            for (int i = 0; i < header.length; i++) {
                String h = header[i].replace("\"", "").trim().toLowerCase();
                if (h.contains(n)) return i;
            }
        }
        return -1;
    }

    private static String at(List<String> cols, int i) {
        if (i < 0 || i >= cols.size()) throw new IllegalArgumentException("missing column " + i);
        return cols.get(i).replace("\"", "").trim();
    }

    static double money(String text) {
        String t = text.replace("₹", "").replace("Rs.", "").replace("Rs", "").replace("INR", "").replace(",", "").trim();
        if (t.isEmpty() || t.equals("-")) return 0;
        return Double.parseDouble(t);
    }

    static LocalDate parseDate(String text) {
        String t = text.trim();
        List<String> candidates = new ArrayList<>(List.of(t));
        if (t.contains("T")) candidates.add(t.substring(0, t.indexOf('T')));
        if (t.contains(",") && !t.matches("[A-Za-z]{3} \\d{1,2}, \\d{4}.*")) candidates.add(t.substring(0, t.indexOf(',')).trim());
        String[] words = t.split("\\s+");
        if (words.length > 1) candidates.add(words[0]);
        if (words.length > 3) candidates.add(words[0] + " " + words[1] + " " + words[2]);
        for (String c : candidates) {
            for (DateTimeFormatter f : DATE_FORMATS) {
                try {
                    return LocalDate.parse(c, f);
                } catch (DateTimeParseException ignored) {
                    // try the next format
                }
            }
        }
        throw new IllegalArgumentException("unreadable date: " + text);
    }
}
