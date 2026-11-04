package com.trustlend.dao.jdbc;

import com.trustlend.dao.VouchDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Vouch;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcVouchDao extends AbstractJdbcDao<Vouch> implements VouchDao {

    public JdbcVouchDao(DatabaseManager db) {
        super(db);
    }

    @Override protected String table() { return "vouches"; }

    @Override
    protected Vouch mapRow(ResultSet rs) throws SQLException {
        return new Vouch(
                rs.getLong("id"),
                rs.getLong("voucher_id"),
                rs.getLong("vouchee_id"),
                rs.getInt("strength"),
                Vouch.Relationship.valueOf(rs.getString("relationship")),
                SqlDates.fromSql(rs.getString("created_date")));
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO vouches (voucher_id, vouchee_id, strength, relationship, created_date) VALUES (?, ?, ?, ?, ?)";
    }

    @Override
    protected String updateSql() {
        return "UPDATE vouches SET voucher_id = ?, vouchee_id = ?, strength = ?, relationship = ?, created_date = ? "
                + "WHERE id = ?";
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Vouch v) throws SQLException {
        ps.setLong(1, v.getVoucherId());
        ps.setLong(2, v.getVoucheeId());
        ps.setInt(3, v.getStrength());
        ps.setString(4, v.getRelationship().name());
        ps.setString(5, SqlDates.toSql(v.getCreatedDate()));
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Vouch v) throws SQLException {
        bindInsert(ps, v);
        ps.setLong(6, v.getId());
    }

    @Override
    public List<Vouch> findGivenBy(long voucherId) throws DataAccessException {
        return query("SELECT * FROM vouches WHERE voucher_id = ?", voucherId);
    }

    @Override
    public List<Vouch> findReceivedBy(long voucheeId) throws DataAccessException {
        return query("SELECT * FROM vouches WHERE vouchee_id = ?", voucheeId);
    }

    @Override
    public Optional<Vouch> find(long voucherId, long voucheeId) throws DataAccessException {
        return query("SELECT * FROM vouches WHERE voucher_id = ? AND vouchee_id = ?", voucherId, voucheeId)
                .stream().findFirst();
    }
}
