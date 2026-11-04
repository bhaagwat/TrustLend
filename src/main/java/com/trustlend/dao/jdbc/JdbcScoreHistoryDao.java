package com.trustlend.dao.jdbc;

import com.trustlend.dao.ScoreHistoryDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.ScoreBreakdown;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JdbcScoreHistoryDao implements ScoreHistoryDao {

    private final DatabaseManager db;

    public JdbcScoreHistoryDao(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void saveAll(Collection<ScoreBreakdown> scores) throws DataAccessException {
        db.inTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement(
                         "DELETE FROM score_history WHERE vendor_id = ? AND computed_on = ?");
                 PreparedStatement ins = conn.prepareStatement(
                         "INSERT INTO score_history (vendor_id, score, consistency, repayment, tenure, network, "
                                 + "computed_on) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (ScoreBreakdown s : scores) {
                    del.setLong(1, s.vendorId());
                    del.setString(2, SqlDates.toSql(s.computedOn()));
                    del.addBatch();

                    ins.setLong(1, s.vendorId());
                    ins.setInt(2, s.score());
                    ins.setDouble(3, s.consistency());
                    ins.setDouble(4, s.repayment());
                    ins.setDouble(5, s.tenure());
                    ins.setDouble(6, s.network());
                    ins.setString(7, SqlDates.toSql(s.computedOn()));
                    ins.addBatch();
                }
                del.executeBatch();
                ins.executeBatch();
            }
            return null;
        });
    }

    @Override
    public List<ScoreBreakdown> findByVendor(long vendorId) throws DataAccessException {
        return db.execute(conn -> {
            List<ScoreBreakdown> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM score_history WHERE vendor_id = ? ORDER BY computed_on")) {
                ps.setLong(1, vendorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new ScoreBreakdown(
                                rs.getLong("vendor_id"),
                                rs.getInt("score"),
                                rs.getDouble("consistency"),
                                rs.getDouble("repayment"),
                                rs.getDouble("tenure"),
                                rs.getDouble("network"),
                                List.of(),
                                SqlDates.fromSql(rs.getString("computed_on"))));
                    }
                }
            }
            return list;
        });
    }
}
