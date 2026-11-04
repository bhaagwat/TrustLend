package com.trustlend.dao.jdbc;

import com.trustlend.dao.TransactionDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Transaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class JdbcTransactionDao extends AbstractJdbcDao<Transaction> implements TransactionDao {

    public JdbcTransactionDao(DatabaseManager db) {
        super(db);
    }

    @Override protected String table() { return "transactions"; }

    @Override protected String defaultOrder() { return "txn_date DESC, id DESC"; }

    @Override
    protected Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                rs.getLong("vendor_id"),
                rs.getDouble("amount"),
                Transaction.Type.valueOf(rs.getString("type")),
                SqlDates.fromSql(rs.getString("txn_date")),
                rs.getString("note"));
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO transactions (vendor_id, amount, type, txn_date, note) VALUES (?, ?, ?, ?, ?)";
    }

    @Override
    protected String updateSql() {
        return "UPDATE transactions SET vendor_id = ?, amount = ?, type = ?, txn_date = ?, note = ? WHERE id = ?";
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Transaction t) throws SQLException {
        ps.setLong(1, t.getVendorId());
        ps.setDouble(2, t.getAmount());
        ps.setString(3, t.getType().name());
        ps.setString(4, SqlDates.toSql(t.getDate()));
        ps.setString(5, t.getNote());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Transaction t) throws SQLException {
        bindInsert(ps, t);
        ps.setLong(6, t.getId());
    }

    @Override
    public List<Transaction> findByVendor(long vendorId) throws DataAccessException {
        return query("SELECT * FROM transactions WHERE vendor_id = ? ORDER BY txn_date DESC, id DESC", vendorId);
    }

    @Override
    public List<Transaction> findByVendorBetween(long vendorId, LocalDate from, LocalDate to)
            throws DataAccessException {
        return query("SELECT * FROM transactions WHERE vendor_id = ? AND txn_date BETWEEN ? AND ? ORDER BY txn_date",
                vendorId, SqlDates.toSql(from), SqlDates.toSql(to));
    }

    @Override
    public List<Transaction> findRecent(int limit) throws DataAccessException {
        return query("SELECT * FROM transactions ORDER BY txn_date DESC, id DESC LIMIT ?", limit);
    }

    @Override
    public int insertBatch(List<Transaction> transactions) throws DataAccessException {
        return db.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(insertSql())) {
                for (Transaction t : transactions) {
                    bindInsert(ps, t);
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                return counts.length;
            }
        });
    }
}
