package com.trustlend.io;

import com.trustlend.config.AppConfig;
import com.trustlend.dao.LoanDao;
import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.db.SqlDates;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Loan;
import com.trustlend.model.Repayment;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.Vouch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Backup &amp; restore.
 * <ul>
 *   <li>{@link #exportSnapshot} – object serialization of every entity to a {@code .tlbak} file</li>
 *   <li>{@link #importSnapshot} – deserialization + full restore inside ONE database transaction</li>
 *   <li>{@link #copyFile} – raw byte-stream copy (used to clone the SQLite file itself)</li>
 * </ul>
 */
public class BackupService {

    private static final Logger LOG = Logger.getLogger(BackupService.class.getName());
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DatabaseManager db;
    private final VendorDao vendorDao;
    private final TransactionDao transactionDao;
    private final LoanDao loanDao;
    private final RepaymentDao repaymentDao;
    private final VouchDao vouchDao;

    public BackupService(DatabaseManager db, VendorDao vendorDao, TransactionDao transactionDao,
                         LoanDao loanDao, RepaymentDao repaymentDao, VouchDao vouchDao) {
        this.db = db;
        this.vendorDao = vendorDao;
        this.transactionDao = transactionDao;
        this.loanDao = loanDao;
        this.repaymentDao = repaymentDao;
        this.vouchDao = vouchDao;
    }

    public static Path defaultBackupFile() {
        return AppConfig.getInstance().getPath("backup.dir")
                .resolve("trustlend-" + STAMP.format(LocalDateTime.now()) + ".tlbak");
    }

    // ------------------------------------------------------------------ serialization

    public BackupSnapshot exportSnapshot(Path file) throws IOException, DataAccessException {
        BackupSnapshot snapshot = new BackupSnapshot(
                AppConfig.getInstance().get("app.version"),
                vendorDao.findAll(), transactionDao.findAll(), loanDao.findAll(),
                repaymentDao.findAll(), vouchDao.findAll());
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            out.writeObject(snapshot);
        }
        LOG.info("Backup written to " + file + " (" + snapshot.summary() + ")");
        return snapshot;
    }

    public BackupSnapshot readSnapshot(Path file) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file.toFile())))) {
            Object obj = in.readObject();
            if (!(obj instanceof BackupSnapshot snapshot)) {      // pattern matching for instanceof
                throw new IOException("Not a TrustLend backup file");
            }
            return snapshot;
        } catch (ClassNotFoundException | InvalidClassException e) {
            throw new IOException("Backup was created by an incompatible version", e);
        }
    }

    /** Replaces ALL data with the snapshot. Original ids are kept so relationships stay intact. */
    public BackupSnapshot importSnapshot(Path file) throws IOException, DataAccessException {
        BackupSnapshot s = readSnapshot(file);
        db.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                for (String t : new String[]{"score_history", "repayments", "vouches", "loans", "transactions", "vendors"}) {
                    st.executeUpdate("DELETE FROM " + t);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vendors (id, name, phone, category, market_area, city, years_in_business, join_date, active) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Vendor v : s.getVendors()) {
                    ps.setLong(1, v.getId());
                    ps.setString(2, v.getName());
                    ps.setString(3, v.getPhone());
                    ps.setString(4, v.getCategory().name());
                    ps.setString(5, v.getMarketArea());
                    ps.setString(6, v.getCity());
                    ps.setInt(7, v.getYearsInBusiness());
                    ps.setString(8, SqlDates.toSql(v.getJoinDate()));
                    ps.setInt(9, v.isActive() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions (id, vendor_id, amount, type, txn_date, note) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (Transaction t : s.getTransactions()) {
                    ps.setLong(1, t.getId());
                    ps.setLong(2, t.getVendorId());
                    ps.setDouble(3, t.getAmount());
                    ps.setString(4, t.getType().name());
                    ps.setString(5, SqlDates.toSql(t.getDate()));
                    ps.setString(6, t.getNote());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO loans (id, vendor_id, principal, interest_rate, tenure_weeks, issue_date, due_date, "
                            + "status, score_at_issue) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Loan l : s.getLoans()) {
                    ps.setLong(1, l.getId());
                    ps.setLong(2, l.getVendorId());
                    ps.setDouble(3, l.getPrincipal());
                    ps.setDouble(4, l.getInterestRate());
                    ps.setInt(5, l.getTenureWeeks());
                    ps.setString(6, SqlDates.toSql(l.getIssueDate()));
                    ps.setString(7, SqlDates.toSql(l.getDueDate()));
                    ps.setString(8, l.getStatus().name());
                    ps.setInt(9, l.getScoreAtIssue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO repayments (id, loan_id, amount, due_date, paid_date, on_time) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (Repayment r : s.getRepayments()) {
                    ps.setLong(1, r.getId());
                    ps.setLong(2, r.getLoanId());
                    ps.setDouble(3, r.getAmount());
                    ps.setString(4, SqlDates.toSql(r.getDueDate()));
                    ps.setString(5, SqlDates.toSql(r.getPaidDate()));
                    ps.setInt(6, r.isOnTime() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vouches (id, voucher_id, vouchee_id, strength, relationship, created_date) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {
                for (Vouch v : s.getVouches()) {
                    ps.setLong(1, v.getId());
                    ps.setLong(2, v.getVoucherId());
                    ps.setLong(3, v.getVoucheeId());
                    ps.setInt(4, v.getStrength());
                    ps.setString(5, v.getRelationship().name());
                    ps.setString(6, SqlDates.toSql(v.getCreatedDate()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
        LOG.info("Restored backup from " + file + " (" + s.summary() + ")");
        return s;
    }

    // ------------------------------------------------------------------ byte streams

    /** Copies any file with a manual byte buffer (demonstrates InputStream/OutputStream). */
    public static long copyFile(Path source, Path target) throws IOException {
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream in = new BufferedInputStream(new FileInputStream(source.toFile()));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }
}
