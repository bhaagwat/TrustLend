package com.trustlend.dao.jdbc;

import com.trustlend.dao.UploadDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Upload;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** JDBC implementation of {@link UploadDao}. */
public class JdbcUploadDao extends AbstractJdbcDao<Upload> implements UploadDao {

    public JdbcUploadDao(DatabaseManager db) {
        super(db);
    }

    @Override protected String table() { return "uploads"; }
    @Override protected String defaultOrder() { return "uploaded_on DESC, id DESC"; }

    @Override
    protected Upload mapRow(ResultSet rs) throws SQLException {
        return new Upload(rs.getLong("id"), rs.getLong("vendor_id"), rs.getString("file_name"),
                SqlDates.fromSql(rs.getString("uploaded_on")), rs.getInt("rows"), rs.getInt("sales_days"),
                rs.getDouble("total_sales"), rs.getInt("quality"), Upload.Status.valueOf(rs.getString("status")),
                rs.getString("issues"));
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO uploads (vendor_id, file_name, uploaded_on, rows, sales_days, total_sales, quality, status, issues) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String updateSql() {
        return "UPDATE uploads SET vendor_id = ?, file_name = ?, uploaded_on = ?, rows = ?, sales_days = ?, "
                + "total_sales = ?, quality = ?, status = ?, issues = ? WHERE id = ?";
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Upload u) throws SQLException {
        ps.setLong(1, u.getVendorId());
        ps.setString(2, u.getFileName());
        ps.setString(3, SqlDates.toSql(u.getUploadedOn()));
        ps.setInt(4, u.getRows());
        ps.setInt(5, u.getSalesDays());
        ps.setDouble(6, u.getTotalSales());
        ps.setInt(7, u.getQuality());
        ps.setString(8, u.getStatus().name());
        ps.setString(9, u.getIssues());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Upload u) throws SQLException {
        bindInsert(ps, u);
        ps.setLong(10, u.getId());
    }

    @Override
    public List<Upload> findByVendor(long vendorId) throws DataAccessException {
        return query("SELECT * FROM uploads WHERE vendor_id = ? ORDER BY uploaded_on DESC, id DESC", vendorId);
    }

    @Override
    public List<Upload> findByStatus(Upload.Status status) throws DataAccessException {
        return query("SELECT * FROM uploads WHERE status = ? ORDER BY uploaded_on DESC, id DESC", status.name());
    }

    @Override
    public void updateStatus(long uploadId, Upload.Status status) throws DataAccessException {
        db.execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE uploads SET status = ? WHERE id = ?")) {
                ps.setString(1, status.name());
                ps.setLong(2, uploadId);
                return ps.executeUpdate();
            }
        });
    }
}
