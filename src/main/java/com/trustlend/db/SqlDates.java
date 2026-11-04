package com.trustlend.db;

import java.time.LocalDate;

/** SQLite has no DATE type, so dates are stored as ISO-8601 text (yyyy-MM-dd) which sorts correctly. */
public final class SqlDates {

    private SqlDates() { }

    public static String toSql(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public static LocalDate fromSql(String text) {
        return text == null ? null : LocalDate.parse(text);
    }
}
