package com.trustlend.dao.jdbc;

import com.trustlend.dao.VendorDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcVendorDao extends AbstractJdbcDao<Vendor> implements VendorDao {

    public JdbcVendorDao(DatabaseManager db) {
        super(db);
    }

    @Override protected String table() { return "vendors"; }

    @Override protected String defaultOrder() { return "name COLLATE NOCASE"; }

    @Override
    protected Vendor mapRow(ResultSet rs) throws SQLException {
        return new Vendor(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("phone"),
                VendorCategory.valueOf(rs.getString("category")),
                rs.getString("market_area"),
                rs.getString("city"),
                rs.getInt("years_in_business"),
                SqlDates.fromSql(rs.getString("join_date")),
                rs.getInt("active") == 1);
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO vendors (name, phone, category, market_area, city, years_in_business, join_date, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String updateSql() {
        return "UPDATE vendors SET name = ?, phone = ?, category = ?, market_area = ?, city = ?, "
                + "years_in_business = ?, join_date = ?, active = ? WHERE id = ?";
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Vendor v) throws SQLException {
        ps.setString(1, v.getName());
        ps.setString(2, v.getPhone());
        ps.setString(3, v.getCategory().name());
        ps.setString(4, v.getMarketArea());
        ps.setString(5, v.getCity());
        ps.setInt(6, v.getYearsInBusiness());
        ps.setString(7, SqlDates.toSql(v.getJoinDate()));
        ps.setInt(8, v.isActive() ? 1 : 0);
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Vendor v) throws SQLException {
        bindInsert(ps, v);
        ps.setLong(9, v.getId());
    }

    @Override
    public Optional<Vendor> findByPhone(String phone) throws DataAccessException {
        return query("SELECT * FROM vendors WHERE phone = ?", phone).stream().findFirst();
    }

    @Override
    public List<Vendor> search(String text) throws DataAccessException {
        String like = "%" + text.trim().toLowerCase() + "%";
        return query("SELECT * FROM vendors WHERE lower(name) LIKE ? OR phone LIKE ? "
                        + "OR lower(market_area) LIKE ? OR lower(city) LIKE ? ORDER BY name COLLATE NOCASE",
                like, like, like, like);
    }
}
