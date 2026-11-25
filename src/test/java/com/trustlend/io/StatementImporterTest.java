package com.trustlend.io;

import com.trustlend.model.Upload;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading UPI / bank statements and the tamper checks. */
class StatementImporterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Test
    void readsCreditsAndIgnoresDebitsAndFailures() throws Exception {
        List<String> lines = new ArrayList<>(List.of("Date,Transaction ID,Description,Type,Amount,Status"));
        for (int d = 1; d <= 14; d++) {
            String date = "2026-11-" + String.format("%02d", d);
            lines.add(date + ",T" + d + "a,Received,CREDIT," + (40 + d) + ".00,SUCCESS");
            lines.add(date + ",T" + d + "b,Received,CREDIT,\"1,2" + d % 10 + "5.00\",SUCCESS");
            lines.add(date + ",T" + d + "c,Paid to supplier,DEBIT,900.00,SUCCESS");
            lines.add(date + ",T" + d + "d,Received,CREDIT,77.00,FAILED");
        }
        StatementImporter.Result r = StatementImporter.analyse(lines, TODAY);
        assertEquals(28, r.credits());
        assertEquals(14, r.days().size());
        assertEquals(41 + 1215, r.days().get(0).amount(), 0.001);     // "1,215.00" written with a comma
        assertEquals(Upload.Status.ACCEPTED, r.status());
    }

    @Test
    void separateCreditDebitColumnsAndOtherDateFormats() throws Exception {
        List<String> lines = new ArrayList<>(List.of("Txn Date,UTR,Narration,Debit,Credit,Balance"));
        for (int d = 1; d <= 12; d++) {
            lines.add(String.format("%02d/11/2026,U%d,UPI/IN,,%d.50,1000", d, d, 150 + d * 3));
            lines.add(String.format("%02d/11/2026,V%d,UPI/OUT,200.00,,800", d, d));
        }
        StatementImporter.Result r = StatementImporter.analyse(lines, TODAY);
        assertEquals(12, r.credits());
        assertEquals(LocalDate.of(2026, 11, 1), r.days().get(0).date());
    }

    @Test
    void flagsCopiedRowsRoundAmountsAndFutureDates() throws Exception {
        List<String> lines = new ArrayList<>(List.of("Date,Transaction ID,Type,Amount"));
        for (int d = 1; d <= 15; d++) {
            String row = "2026-11-" + String.format("%02d", d) + ",X" + (d % 5) + ",CREDIT,1000.00";   // ids repeat, all round
            lines.add(row);
        }
        lines.add("2026-12-25,FUT,CREDIT,1500.00");
        StatementImporter.Result r = StatementImporter.analyse(lines, TODAY);
        assertTrue(r.quality() < StatementImporter.ACCEPT_AT, "quality " + r.quality());
        assertTrue(r.issues().stream().anyMatch(i -> i.contains("duplicate")));
        assertTrue(r.issues().stream().anyMatch(i -> i.contains("round")));
        assertTrue(r.issues().stream().anyMatch(i -> i.contains("future")));
        assertEquals(Upload.Status.REJECTED, r.status());
    }

    @Test
    void needsADateAndAnAmount() {
        assertThrows(java.io.IOException.class, () -> StatementImporter.analyse(List.of("Name,Phone", "A,1"), TODAY));
        assertThrows(java.io.IOException.class, () -> StatementImporter.analyse(List.of("Date,Name", "2026-11-01,A"), TODAY));
    }
}
