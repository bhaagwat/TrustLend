package com.trustlend.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatting helpers shared by UI, reports and exports. */
public final class Formats {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private Formats() { }

    /**
     * ₹1,25,000 style grouping (Indian lakh format): last three digits, then groups of two.
     * java.text.DecimalFormat only supports one grouping size, so we group by hand.
     */
    public static String inr(double amount) {
        long value = Math.round(Math.abs(amount));
        String digits = Long.toString(value);
        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        if (len <= 3) {
            sb.append(digits);
        } else {
            String head = digits.substring(0, len - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) sb.append(',');
                sb.append(head.charAt(i));
            }
            sb.append(',').append(digits, len - 3, len);
        }
        return (amount < 0 ? "-₹" : "₹") + sb;
    }

    /** Compact form for dashboards: ₹4.2L, ₹1.1Cr. */
    public static String inrCompact(double amount) {
        if (amount >= 1_00_00_000) return String.format("₹%.1fCr", amount / 1_00_00_000);
        if (amount >= 1_00_000) return String.format("₹%.1fL", amount / 1_00_000);
        if (amount >= 1_000) return String.format("₹%.1fK", amount / 1_000);
        return inr(amount);
    }

    public static String date(LocalDate d) {
        return d == null ? "—" : DATE.format(d);
    }

    public static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }
}
