package com.trustlend.dao.jdbc;

import com.trustlend.dao.IncomeDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.IncomeDeclaration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** JDBC implementation of {@link IncomeDao}. */
public class JdbcIncomeDao extends AbstractJdbcDao<IncomeDeclaration> implements IncomeDao {

    public JdbcIncomeDao(DatabaseManager db) {
        super(db);
    }

    @Override protected String table() { return "incomes"; }
    @Override protected String defaultOrder() { return "declared_on DESC, id DESC"; }

    @Override
    protected IncomeDeclaration mapRow(ResultSet rs) throws SQLException {
        return new IncomeDeclaration(rs.getLong("id"), rs.getLong("vendor_id"), rs.getDouble("monthly_amount"),
                IncomeDeclaration.Kind.valueOf(rs.getString("kind")), rs.getString("proof_file"),
                SqlDates.fromSql(rs.getString("declared_on")), IncomeDeclaration.Status.valueOf(rs.getString("status")),
                rs.getLong("verified_by"));
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO incomes (vendor_id, monthly_amount, kind, proof_file, declared_on, status, verified_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String updateSql() {
        return "UPDATE incomes SET vendor_id = ?, monthly_amount = ?, kind = ?, proof_file = ?, declared_on = ?, "
                + "status = ?, verified_by = ? WHERE id = ?";
    }

    @Override
    protected void bindInsert(PreparedStatement ps, IncomeDeclaration d) throws SQLException {
        ps.setLong(1, d.getVendorId());
        ps.setDouble(2, d.getMonthlyAmount());
        ps.setString(3, d.getKind().name());
        ps.setString(4, d.getProofFile());
        ps.setString(5, SqlDates.toSql(d.getDeclaredOn()));
        ps.setString(6, d.getStatus().name());
        if (d.getVerifiedBy() > 0) ps.setLong(7, d.getVerifiedBy());
        else ps.setNull(7, Types.INTEGER);
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, IncomeDeclaration d) throws SQLException {
        bindInsert(ps, d);
        ps.setLong(8, d.getId());
    }

    @Override
    public List<IncomeDeclaration> findByVendor(long vendorId) throws DataAccessException {
        return query("SELECT * FROM incomes WHERE vendor_id = ? ORDER BY declared_on DESC, id DESC", vendorId);
    }

    @Override
    public List<IncomeDeclaration> findByStatus(IncomeDeclaration.Status status) throws DataAccessException {
        return query("SELECT * FROM incomes WHERE status = ? ORDER BY declared_on DESC, id DESC", status.name());
    }
}
