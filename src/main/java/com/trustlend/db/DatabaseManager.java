package com.trustlend.db;

import com.trustlend.exception.DataAccessException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the JDBC {@link Connection} and offers transaction management (Unit 4).
 *
 * <p>JDBC architecture in one line: our code → JDBC API (java.sql) → DriverManager →
 * SQLite JDBC driver (org.xerial) → database file.</p>
 *
 * <p>A desktop app with an embedded SQLite file is best served by one shared connection,
 * so every DAO receives this manager through its constructor (dependency injection).</p>
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());

    private final String url;
    private Connection connection;

    private DatabaseManager(String url) {
        this.url = url;
    }

    /** Opens (and creates if needed) a database file on disk. */
    public static DatabaseManager forFile(Path file) throws DataAccessException {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot create database folder " + file.getParent(), e);
        }
        DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + file.toAbsolutePath());
        db.connect();
        return db;
    }

    /** Throw-away in-memory database, used by unit tests. */
    public static DatabaseManager inMemory() throws DataAccessException {
        DatabaseManager db = new DatabaseManager("jdbc:sqlite::memory:");
        db.connect();
        return db;
    }

    private void connect() throws DataAccessException {
        try {
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");      // SQLite needs this per connection
                st.execute("PRAGMA journal_mode = WAL");
            }
            LOG.info("Connected to " + url);
        } catch (SQLException e) {
            throw new DataAccessException("Unable to open database " + url, e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /** Runs every statement in /db/schema.sql (idempotent: CREATE ... IF NOT EXISTS). */
    public void initSchema() throws DataAccessException {
        List<String> statements = readSqlScript("/db/schema.sql");
        inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                for (String sql : statements) {
                    st.execute(sql);
                }
            }
            return null;
        });
        LOG.info("Schema ready (" + statements.size() + " statements)");
    }

    /** Character-stream reading of a SQL resource, split on ';' and stripped of comments. */
    static List<String> readSqlScript(String resource) throws DataAccessException {
        List<String> statements = new ArrayList<>();
        try (InputStream in = DatabaseManager.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new DataAccessException("SQL script not found: " + resource);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder current = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    statements.add(current.toString().trim().replaceAll(";$", ""));
                    current.setLength(0);
                }
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + resource, e);
        }
        return statements;
    }

    /**
     * Executes {@code work} inside a single database transaction:
     * commit on success, rollback on any failure. Nested calls join the outer transaction.
     */
    public <T> T inTransaction(SqlWork<T> work) throws DataAccessException {
        try {
            boolean outer = connection.getAutoCommit();
            if (!outer) {
                return work.execute(connection);   // already inside a transaction
            }
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                LOG.log(Level.WARNING, "Transaction rolled back", e);
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database operation failed: " + e.getMessage(), e);
        }
    }

    /** Service-level unit of work made of several DAO calls. */
    @FunctionalInterface
    public interface DaoWork<T> {
        T run() throws DataAccessException;
    }

    /**
     * Groups several DAO calls into one transaction (used by the service layer).
     * Because every DAO shares this connection, turning auto-commit off here covers all of them.
     */
    public <T> T transaction(DaoWork<T> work) throws DataAccessException {
        try {
            if (!connection.getAutoCommit()) {
                return work.run();
            }
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot start transaction", e);
        }
        try {
            T result = work.run();
            connection.commit();
            return result;
        } catch (DataAccessException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            LOG.log(Level.WARNING, "Transaction rolled back: " + e.getMessage());
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Commit failed", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                LOG.log(Level.SEVERE, "Could not restore auto-commit", e);
            }
        }
    }

    /** Runs a statement without an explicit transaction and wraps SQLExceptions. */
    public <T> T execute(SqlWork<T> work) throws DataAccessException {
        try {
            return work.execute(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Database operation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOG.info("Database connection closed");
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error closing connection", e);
        }
    }
}
