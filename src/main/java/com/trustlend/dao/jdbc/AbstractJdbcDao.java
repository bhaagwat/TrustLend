package com.trustlend.dao.jdbc;

import com.trustlend.dao.GenericDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Identifiable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Template-method base class: the generic CRUD plumbing lives here once,
 * sub-classes only supply the table name, row mapping and INSERT/UPDATE SQL.
 */
public abstract class AbstractJdbcDao<T extends Identifiable> implements GenericDao<T> {

    protected final DatabaseManager db;

    protected AbstractJdbcDao(DatabaseManager db) {
        this.db = db;
    }

    // ----- hooks implemented by sub-classes ------------------------------------------------

    protected abstract String table();

    /** Converts the current ResultSet row into an entity. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    protected abstract String insertSql();

    protected abstract String updateSql();

    /** Binds entity fields to the INSERT statement parameters. */
    protected abstract void bindInsert(PreparedStatement ps, T entity) throws SQLException;

    /** Binds fields AND the trailing "WHERE id = ?" parameter. */
    protected abstract void bindUpdate(PreparedStatement ps, T entity) throws SQLException;

    /** Default ordering for findAll(). */
    protected String defaultOrder() {
        return "id";
    }

    // ----- shared CRUD --------------------------------------------------------------------

    @Override
    public T save(T entity) throws DataAccessException {
        return db.execute(conn -> entity.isPersisted() ? update(conn, entity) : insert(conn, entity));
    }

    private T insert(Connection conn, T entity) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(insertSql(), Statement.RETURN_GENERATED_KEYS)) {
            bindInsert(ps, entity);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entity.setId(keys.getLong(1));
                }
            }
        }
        return entity;
    }

    private T update(Connection conn, T entity) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(updateSql())) {
            bindUpdate(ps, entity);
            if (ps.executeUpdate() == 0) {
                throw new SQLException(table() + " row " + entity.getId() + " no longer exists");
            }
        }
        return entity;
    }

    @Override
    public Optional<T> findById(long id) throws DataAccessException {
        List<T> rows = query("SELECT * FROM " + table() + " WHERE id = ?", id);
        return rows.stream().findFirst();
    }

    @Override
    public List<T> findAll() throws DataAccessException {
        return query("SELECT * FROM " + table() + " ORDER BY " + defaultOrder());
    }

    @Override
    public boolean delete(long id) throws DataAccessException {
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table() + " WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public long count() throws DataAccessException {
        return db.execute(conn -> {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table())) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    /**
     * Runs a parameterised SELECT. Parameters are bound with setObject, so values are never
     * concatenated into SQL (protection against SQL injection).
     */
    protected List<T> query(String sql, Object... params) throws DataAccessException {
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            }
        });
    }
}
