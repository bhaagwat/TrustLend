package com.trustlend.io;

import com.trustlend.model.Loan;
import com.trustlend.model.RiskBand;
import com.trustlend.model.ScoreBreakdown;
import com.trustlend.model.Vendor;
import com.trustlend.service.AnalyticsService;
import com.trustlend.util.Formats;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Writes a plain-text portfolio report with PrintWriter (formatted character output). */
public class ReportWriter {

    private static final String LINE = "=".repeat(78);
    private static final String THIN = "-".repeat(78);

    public void writePortfolioReport(Path file, AnalyticsService.Stats stats, List<Vendor> ranked,
                                     List<Loan> loans, Map<Long, ScoreBreakdown> scores) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println(LINE);
            out.println(center("TRUSTLEND — PORTFOLIO & TRUST SCORE REPORT"));
            out.println(center("Generated " + DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").format(LocalDateTime.now())));
            out.println(LINE);
            out.println();

            out.println("SUMMARY");
            out.println(THIN);
            out.printf("  %-32s %12d%n", "Registered vendors", stats.vendors());
            out.printf("  %-32s %12.0f%n", "Average trust score", stats.averageScore());
            out.printf("  %-32s %12d%n", "Vouches in the trust graph", stats.vouches());
            out.printf("  %-32s %12d%n", "Open loans", stats.openLoans());
            out.printf("  %-32s %12s%n", "Outstanding", Formats.inr(stats.outstanding()));
            out.printf("  %-32s %12s%n", "Total disbursed", Formats.inr(stats.totalDisbursed()));
            out.printf("  %-32s %12s%n", "On-time instalments", Formats.percent(stats.onTimeRate()));
            out.printf("  %-32s %12d%n", "Defaulted loans", stats.defaulted());
            out.println();

            out.println("RISK BAND DISTRIBUTION");
            out.println(THIN);
            for (RiskBand b : RiskBand.values()) {
                int n = stats.bandCounts().getOrDefault(b, 0);
                out.printf("  %-10s %4d  %s%n", b.getLabel(), n, "█".repeat(n));
            }
            out.println();

            out.println("VENDORS BY TRUST SCORE");
            out.println(THIN);
            out.printf("  %-4s %-24s %-22s %5s %-9s %6s %6s%n", "#", "Name", "Business", "Score", "Band", "Repay", "Netwk");
            int rank = 1;
            for (Vendor v : ranked) {
                ScoreBreakdown s = scores.get(v.getId());
                out.printf("  %-4d %-24s %-22s %5d %-9s %6s %6s%n", rank++, trim(v.getName(), 24),
                        trim(v.getCategory().getLabel(), 22), v.getCurrentScore(), v.getRiskBand().getLabel(),
                        s == null ? "-" : Formats.percent(s.repayment()), s == null ? "-" : Formats.percent(s.network()));
            }
            out.println();

            out.println("LOAN BOOK");
            out.println(THIN);
            out.printf("  %-5s %-24s %10s %10s %-10s %-12s%n", "Loan", "Vendor", "Principal", "Repaid", "Status", "Due");
            for (Loan l : loans) {
                out.printf("  %-5d %-24s %10s %10s %-10s %-12s%n", l.getId(), trim(l.getVendorName(), 24),
                        Formats.inr(l.getPrincipal()), Formats.inr(l.getAmountRepaid()), l.getStatus().getLabel(),
                        Formats.date(l.getDueDate()));
            }
            out.println();
            out.println(LINE);
            out.println(center("End of report"));
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String center(String s) {
        int pad = Math.max(0, (LINE.length() - s.length()) / 2);
        return " ".repeat(pad) + s;
    }
}
