package com.trustlend.dao.jdbc;

import com.trustlend.dao.UserDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

public class JdbcUserDao implements UserDao {

    private final DatabaseManager db;

    public JdbcUserDao(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public Optional<User> findByUsername(String username) throws DataAccessException {
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new User(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("full_name"),
                            User.Role.valueOf(rs.getString("role"))));
                }
            }
        });
    }

    @Override
    public User save(User user) throws DataAccessException {
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, salt, full_name, role) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getPasswordHash());
                ps.setString(3, user.getSalt());
                ps.setString(4, user.getFullName());
                ps.setString(5, user.getRole().name());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setId(keys.getLong(1));
                    }
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
