package com.trustlend.io;

import java.util.ArrayList;
import java.util.List;

/** Splits one CSV line into fields, honouring double quotes ("1,23,456" stays one field). */
final class CsvUtil {

    private CsvUtil() { }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');           // escaped quote
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        out.add(field.toString());
        return out;
    }
}
