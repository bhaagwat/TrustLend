package com.trustlend.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A unit of database work that may throw {@link SQLException}.
 * Standard {@code java.util.function} interfaces cannot throw checked exceptions,
 * so we declare our own generic functional interface.
 */
@FunctionalInterface
public interface SqlWork<T> {
    T execute(Connection connection) throws SQLException;
}
