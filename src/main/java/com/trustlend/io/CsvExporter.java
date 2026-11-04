package com.trustlend.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Generic CSV writer: describe the columns once with lambdas, export any list of T.
 *
 * <pre>
 * new CsvExporter&lt;Vendor&gt;()
 *     .column("Name", Vendor::getName)
 *     .column("Score", v -&gt; String.valueOf(v.getCurrentScore()))
 *     .write(path, vendors);
 * </pre>
 */
public class CsvExporter<T> {

    private record Column<T>(String header, Function<T, ?> extractor) { }

    private final List<Column<T>> columns = new ArrayList<>();

    /** Fluent builder method. */
    public CsvExporter<T> column(String header, Function<T, ?> extractor) {
        columns.add(new Column<>(header, extractor));
        return this;
    }

    /** Writes a UTF-8 CSV with a BOM so Excel shows ₹ and Indian names correctly. */
    public int write(Path file, List<T> rows) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write('﻿');
            List<String> headers = new ArrayList<>();
            for (Column<T> c : columns) headers.add(escape(c.header()));
            out.write(String.join(",", headers));
            out.newLine();
            for (T row : rows) {
                List<String> cells = new ArrayList<>();
                for (Column<T> c : columns) {
                    Object value = c.extractor().apply(row);
                    cells.add(escape(value == null ? "" : value.toString()));
                }
                out.write(String.join(",", cells));
                out.newLine();
            }
        }
        return rows.size();
    }

    /** RFC 4180 quoting. */
    static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
