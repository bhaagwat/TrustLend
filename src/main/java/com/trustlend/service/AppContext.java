package com.trustlend.service;

import com.trustlend.dao.LoanDao;
import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.ScoreHistoryDao;
import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.dao.jdbc.JdbcLoanDao;
import com.trustlend.dao.jdbc.JdbcRepaymentDao;
import com.trustlend.dao.jdbc.JdbcScoreHistoryDao;
import com.trustlend.dao.jdbc.JdbcTransactionDao;
import com.trustlend.dao.jdbc.JdbcVendorDao;
import com.trustlend.dao.jdbc.JdbcVouchDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.engine.EngineSettings;
import com.trustlend.exception.DataAccessException;
import com.trustlend.io.BackupService;
import com.trustlend.io.CsvTransactionImporter;

import java.nio.file.Path;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.logging.Logger;

/**
 * Composition root for one user's workspace: builds the object graph (DB → DAOs → services) by hand.
 * This is "manual dependency injection" — every class receives what it needs via its constructor.
 * A new context is created at every login (see {@link AccountManager#openWorkspace}).
 */
public final class AppContext implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AppContext.class.getName());

    private final DatabaseManager db;
    private final Path databaseFile;
    private final VendorDao vendorDao;
    private final TransactionDao transactionDao;
    private final LoanDao loanDao;
    private final RepaymentDao repaymentDao;
    private final VouchDao vouchDao;

    private final ScoreService scoreService;
    private final VendorService vendorService;
    private final LoanService loanService;
    private final VouchService vouchService;
    private final TransactionService transactionService;
    private final AnalyticsService analyticsService;
    private final BackupService backupService;
    private final CsvTransactionImporter importer;

    /** In-memory or test use: no file on disk. */
    public AppContext(DatabaseManager db, EngineSettings settings) {
        this(db, settings, null);
    }

    public AppContext(DatabaseManager db, EngineSettings settings, Path databaseFile) {
        this.db = db;
        this.databaseFile = databaseFile;
        vendorDao = new JdbcVendorDao(db);
        transactionDao = new JdbcTransactionDao(db);
        loanDao = new JdbcLoanDao(db);
        repaymentDao = new JdbcRepaymentDao(db);
        vouchDao = new JdbcVouchDao(db);
        ScoreHistoryDao historyDao = new JdbcScoreHistoryDao(db);

        scoreService = new ScoreService(vendorDao, transactionDao, loanDao, repaymentDao, vouchDao, historyDao, settings);
        vendorService = new VendorService(vendorDao, scoreService);
        loanService = new LoanService(db, loanDao, repaymentDao, transactionDao, vendorDao, scoreService);
        vouchService = new VouchService(vouchDao, vendorDao, scoreService);
        transactionService = new TransactionService(transactionDao, vendorDao);
        analyticsService = new AnalyticsService(vendorService, loanService, repaymentDao, vouchDao);
        backupService = new BackupService(db, vendorDao, transactionDao, loanDao, repaymentDao, vouchDao);
        importer = new CsvTransactionImporter(vendorDao);
    }

    /** Optionally seeds demo data into an empty workspace, then refreshes overdue loans and scores. */
    public void startUp(boolean seedDemo) throws DataAccessException {
        LocalDate today = LocalDate.now();
        if (seedDemo && isEmpty()) {
            seedDemo(today);
        }
        loanService.refreshOverdue(today);
        scoreService.recalculateAll(today);
    }

    /** A brand-new account has no vendors yet. */
    public boolean isEmpty() throws DataAccessException {
        return vendorDao.count() == 0;
    }

    /**
     * Fills an empty workspace with the demo community (36 vendors in 5 Delhi markets, 5 months of
     * transactions, loans, repayments and vouches) and computes every score.
     *
     * @return number of vendors created
     */
    public long loadDemoData() throws DataAccessException {
        if (!isEmpty()) {
            throw new DataAccessException("Demo data can only be loaded into an empty workspace - clear it first");
        }
        LocalDate today = LocalDate.now();
        seedDemo(today);
        loanService.refreshOverdue(today);
        scoreService.recalculateAll(today);
        return vendorDao.count();
    }

    private void seedDemo(LocalDate today) throws DataAccessException {
        LOG.info("Seeding demo community");
        new DemoDataSeeder(db, vendorDao, transactionDao, loanDao, repaymentDao, vouchDao).seed(today);
        scoreService.backfillHistory(today, 12, 2);
    }

    /** Deletes every vendor; foreign keys with ON DELETE CASCADE remove all their related rows. */
    public void clearWorkspace() throws DataAccessException {
        db.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM vendors");
                st.executeUpdate("DELETE FROM sqlite_sequence");   // ids start from 1 again
            }
            return null;
        });
        scoreService.recalculateAll(LocalDate.now());
        LOG.info("Workspace cleared");
    }

    /** The workspace file on disk (null for in-memory databases). */
    public Path databaseFile() {
        return databaseFile;
    }

    public DatabaseManager db() { return db; }
    public ScoreService scores() { return scoreService; }
    public VendorService vendors() { return vendorService; }
    public LoanService loans() { return loanService; }
    public VouchService vouches() { return vouchService; }
    public TransactionService transactions() { return transactionService; }
    public AnalyticsService analytics() { return analyticsService; }
    public BackupService backup() { return backupService; }
    public CsvTransactionImporter importer() { return importer; }

    @Override
    public void close() {
        db.close();
    }
}
