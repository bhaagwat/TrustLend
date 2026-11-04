package com.trustlend.io;

import com.trustlend.dao.VendorDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads transactions from a CSV file (character stream) so that ledgers kept in a spreadsheet
 * or exported from a UPI app can be bulk-loaded.
 *
 * <pre>phone,date,type,amount,note
 * 9876543210,2026-10-01,SALE,1450,Morning market</pre>
 *
 * Bad lines are skipped and reported instead of aborting the whole import.
 */
public class CsvTransactionImporter {

    public record Result(List<Transaction> transactions, List<String> errors, int linesRead) { }

    private final VendorDao vendorDao;

    public CsvTransactionImporter(VendorDao vendorDao) {
        this.vendorDao = vendorDao;
    }

    public Result parse(Path file) throws IOException, DataAccessException {
        Map<String, Vendor> byPhone = new HashMap<>();
        for (Vendor v : vendorDao.findAll()) byPhone.put(v.getPhone(), v);

        List<Transaction> ok = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int lineNo = 0;
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                lineNo++;
                line = line.replace("﻿", "").trim();
                if (line.isEmpty() || (lineNo == 1 && line.toLowerCase().startsWith("phone"))) {
                    continue;   // header or blank
                }
                String[] parts = line.split(",", 5);
                try {
                    if (parts.length < 4) throw new IllegalArgumentException("expected at least 4 columns");
                    Vendor v = byPhone.get(parts[0].trim());
                    if (v == null) throw new IllegalArgumentException("unknown vendor phone " + parts[0].trim());
                    LocalDate date = LocalDate.parse(parts[1].trim());
                    if (date.isAfter(LocalDate.now())) throw new IllegalArgumentException("date in the future");
                    Transaction.Type type = Transaction.Type.valueOf(parts[2].trim().toUpperCase());
                    if (type == Transaction.Type.LOAN_DISBURSAL) throw new IllegalArgumentException("loan disbursals cannot be imported");
                    double amount = Double.parseDouble(parts[3].trim());
                    if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
                    String note = parts.length > 4 ? parts[4].trim() : "Imported";
                    ok.add(new Transaction(v.getId(), amount, type, date, note));
                } catch (DateTimeParseException e) {
                    errors.add("Line " + lineNo + ": bad date (use yyyy-MM-dd)");
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNo + ": amount is not a number");
                } catch (IllegalArgumentException e) {   // also catches bad enum names
                    errors.add("Line " + lineNo + ": " + e.getMessage());
                }
            }
        }
        return new Result(ok, errors, lineNo);
    }

    /** Writes a sample file the user can open and fill in. */
    public static void writeTemplate(Path file, String samplePhone) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, "phone,date,type,amount,note\n"
                + samplePhone + "," + LocalDate.now().minusDays(1) + ",SALE,1250,Evening market\n"
                + samplePhone + "," + LocalDate.now() + ",DEPOSIT,300,Weekly savings\n", StandardCharsets.UTF_8);
    }
}
