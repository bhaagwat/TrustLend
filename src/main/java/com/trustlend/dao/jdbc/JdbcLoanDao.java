package com.trustlend.dao.jdbc;

import com.trustlend.dao.LoanDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JdbcLoanDao extends AbstractJdbcDao<Loan> implements LoanDao {

    public JdbcLoanDao(DatabaseManager db) {
        super(db);
    }

    @Override protected String table() { return "loans"; }

    @Override protected String defaultOrder() { return "issue_date DESC, id DESC"; }

    @Override
    protected Loan mapRow(ResultSet rs) throws SQLException {
        return new Loan(
                rs.getLong("id"),
                rs.getLong("vendor_id"),
                rs.getDouble("principal"),
                rs.getDouble("interest_rate"),
                rs.getInt("tenure_weeks"),
                SqlDates.fromSql(rs.getString("issue_date")),
                SqlDates.fromSql(rs.getString("due_date")),
                LoanStatus.valueOf(rs.getString("status")),
                rs.getInt("score_at_issue"));
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO loans (vendor_id, principal, interest_rate, tenure_weeks, issue_date, due_date, "
                + "status, score_at_issue) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String updateSql() {
        return "UPDATE loans SET vendor_id = ?, principal = ?, interest_rate = ?, tenure_weeks = ?, issue_date = ?, "
                + "due_date = ?, status = ?, score_at_issue = ? WHERE id = ?";
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Loan l) throws SQLException {
        ps.setLong(1, l.getVendorId());
        ps.setDouble(2, l.getPrincipal());
        ps.setDouble(3, l.getInterestRate());
        ps.setInt(4, l.getTenureWeeks());
        ps.setString(5, SqlDates.toSql(l.getIssueDate()));
        ps.setString(6, SqlDates.toSql(l.getDueDate()));
        ps.setString(7, l.getStatus().name());
        ps.setInt(8, l.getScoreAtIssue());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Loan l) throws SQLException {
        bindInsert(ps, l);
        ps.setLong(9, l.getId());
    }

    @Override
    public List<Loan> findByVendor(long vendorId) throws DataAccessException {
        return query("SELECT * FROM loans WHERE vendor_id = ? ORDER BY issue_date DESC", vendorId);
    }

    @Override
    public List<Loan> findByStatus(LoanStatus status) throws DataAccessException {
        return query("SELECT * FROM loans WHERE status = ? ORDER BY due_date", status.name());
    }

    @Override
    public void updateStatus(long loanId, LoanStatus status) throws DataAccessException {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE loans SET status = ? WHERE id = ?")) {
                ps.setString(1, status.name());
                ps.setLong(2, loanId);
                return ps.executeUpdate();
            }
        });
    }
}
