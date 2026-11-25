package com.trustlend.dao.jdbc;

import com.trustlend.dao.UserDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link UserDao}. Passwords are stored only as salted hashes. */
public class JdbcUserDao implements UserDao {

    private final DatabaseManager db;

    public JdbcUserDao(DatabaseManager db) {
        this.db = db;
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                rs.getString("full_name"),
                User.Role.valueOf(rs.getString("role")),
                rs.getLong("vendor_id"),
                rs.getString("organisation"),
                rs.getString("lender_type"));
    }

    private List<User> query(String sql, Object... params) throws DataAccessException {
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    List<User> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            }
        });
    }

    @Override
    public Optional<User> findByUsername(String username) throws DataAccessException {
        return query("SELECT * FROM users WHERE username = ?", username).stream().findFirst();
    }

    @Override
    public Optional<User> findById(long id) throws DataAccessException {
        return query("SELECT * FROM users WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public Optional<User> findByVendor(long vendorId) throws DataAccessException {
        return query("SELECT * FROM users WHERE vendor_id = ?", vendorId).stream().findFirst();
    }

    @Override
    public List<User> findAll() throws DataAccessException {
        return query("SELECT * FROM users ORDER BY role, full_name");
    }

    @Override
    public List<User> findByRole(User.Role role) throws DataAccessException {
        return query("SELECT * FROM users WHERE role = ? ORDER BY full_name", role.name());
    }

    @Override
    public User save(User user) throws DataAccessException {
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, salt, full_name, role, vendor_id, organisation, lender_type) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getPasswordHash());
                ps.setString(3, user.getSalt());
                ps.setString(4, user.getFullName());
                ps.setString(5, user.getRole().name());
                if (user.getVendorId() > 0) ps.setLong(6, user.getVendorId());
                else ps.setNull(6, Types.INTEGER);
                ps.setString(7, user.getOrganisation());
                ps.setString(8, user.getLenderType());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) user.setId(keys.getLong(1));
                }
                return user;
            }
        });
    }

    @Override
    public long count() throws DataAccessException {
        return db.execute(conn -> {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }
}
