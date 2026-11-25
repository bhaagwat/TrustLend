package com.trustlend.dao.jdbc;

import com.trustlend.dao.RepaymentDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Repayment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcRepaymentDao extends AbstractJdbcDao<Repayment> implements RepaymentDao {

    public JdbcRepaymentDao(DatabaseManager db) {
        super(db);
    }

    @Override protected String table() { return "repayments"; }

    @Override protected String defaultOrder() { return "paid_date DESC"; }

    @Override
    protected Repayment mapRow(ResultSet rs) throws SQLException {
        Repayment r = new Repayment(
                rs.getLong("id"),
                rs.getLong("loan_id"),
                rs.getDouble("amount"),
                SqlDates.fromSql(rs.getString("due_date")),
                SqlDates.fromSql(rs.getString("paid_date")),
                rs.getInt("on_time") == 1);
        r.setStatus(Repayment.Verification.valueOf(rs.getString("status")));
        r.setReportedBy(rs.getLong("reported_by"));
        r.setNote(rs.getString("note"));
        return r;
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO repayments (loan_id, amount, due_date, paid_date, on_time, status, reported_by, note) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String updateSql() {
        return "UPDATE repayments SET loan_id = ?, amount = ?, due_date = ?, paid_date = ?, on_time = ?, status = ?, "
                + "reported_by = ?, note = ? WHERE id = ?";
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Repayment r) throws SQLException {
        ps.setLong(1, r.getLoanId());
        ps.setDouble(2, r.getAmount());
        ps.setString(3, SqlDates.toSql(r.getDueDate()));
        ps.setString(4, SqlDates.toSql(r.getPaidDate()));
        ps.setInt(5, r.isOnTime() ? 1 : 0);
        ps.setString(6, r.getStatus().name());
        if (r.getReportedBy() > 0) ps.setLong(7, r.getReportedBy());
        else ps.setNull(7, java.sql.Types.INTEGER);
        ps.setString(8, r.getNote());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Repayment r) throws SQLException {
        bindInsert(ps, r);
        ps.setLong(9, r.getId());
    }

    @Override
    public List<Repayment> findByLoan(long loanId) throws DataAccessException {
        return query("SELECT * FROM repayments WHERE loan_id = ? ORDER BY paid_date", loanId);
    }

    @Override
    public List<Repayment> findByVendor(long vendorId) throws DataAccessException {
        return query("SELECT r.* FROM repayments r JOIN loans l ON l.id = r.loan_id "
                + "WHERE l.vendor_id = ? ORDER BY r.paid_date", vendorId);
    }

    @Override
    public double totalRepaid(long loanId) throws DataAccessException {
        return db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM repayments WHERE loan_id = ? AND status IN ('CONFIRMED', 'PENDING')")) {
                ps.setLong(1, loanId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0.0;
                }
            }
        });
    }

    @Override
    public Map<Long, Double> totalsByLoan() throws DataAccessException {
        return db.execute(conn -> {
            Map<Long, Double> totals = new HashMap<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT loan_id, SUM(amount) FROM repayments "
                         + "WHERE status IN ('CONFIRMED', 'PENDING') GROUP BY loan_id")) {
                while (rs.next()) {
                    totals.put(rs.getLong(1), rs.getDouble(2));
                }
            }
            return totals;
        });
    }

    @Override
    public List<Repayment> findByStatus(Repayment.Verification status) throws DataAccessException {
        return query("SELECT * FROM repayments WHERE status = ? ORDER BY paid_date DESC", status.name());
    }

    @Override
    public List<Repayment> findByStatusForVendor(long vendorId, Repayment.Verification status) throws DataAccessException {
        return query("SELECT r.* FROM repayments r JOIN loans l ON l.id = r.loan_id "
                + "WHERE l.vendor_id = ? AND r.status = ? ORDER BY r.paid_date", vendorId, status.name());
    }

    @Override
    public List<Repayment> findReportedBy(long userId) throws DataAccessException {
        return query("SELECT * FROM repayments WHERE reported_by = ? ORDER BY paid_date DESC", userId);
    }

    @Override
    public void updateStatus(long repaymentId, Repayment.Verification status, String note) throws DataAccessException {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE repayments SET status = ?, note = ? WHERE id = ?")) {
                ps.setString(1, status.name());
                ps.setString(2, note);
                ps.setLong(3, repaymentId);
                return ps.executeUpdate();
            }
        });
    }
}
