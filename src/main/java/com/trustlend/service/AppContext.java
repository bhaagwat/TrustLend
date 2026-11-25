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
import com.trustlend.dao.UploadDao;
import com.trustlend.dao.IncomeDao;
import com.trustlend.dao.UserDao;
import com.trustlend.dao.jdbc.JdbcIncomeDao;
import com.trustlend.dao.jdbc.JdbcUploadDao;
import com.trustlend.dao.jdbc.JdbcUserDao;
import com.trustlend.config.AppConfig;
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
 * Composition root for the shared community database: builds the object graph (DB → DAOs → services) by hand.
 * This is "manual dependency injection" — every class receives what it needs via its constructor.
 * One context is opened when the app starts ({@link #open()}) and shared by every role that logs in.
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
    private final UserDao userDao;
    private final UploadDao uploadDao;
    private final IncomeDao incomeDao;
    private final AuthService authService;
    private final CommunityService communityService;

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
        userDao = new JdbcUserDao(db);
        uploadDao = new JdbcUploadDao(db);
        incomeDao = new JdbcIncomeDao(db);

        scoreService = new ScoreService(vendorDao, transactionDao, loanDao, repaymentDao, vouchDao, historyDao, incomeDao, settings);
        vendorService = new VendorService(vendorDao, scoreService);
        loanService = new LoanService(db, loanDao, repaymentDao, transactionDao, vendorDao, scoreService);
        vouchService = new VouchService(vouchDao, vendorDao, scoreService);
        transactionService = new TransactionService(transactionDao, vendorDao);
        analyticsService = new AnalyticsService(vendorService, loanService, repaymentDao, vouchDao);
        backupService = new BackupService(db, vendorDao, transactionDao, loanDao, repaymentDao, vouchDao);
        importer = new CsvTransactionImporter(vendorDao);
        authService = new AuthService(userDao);
        communityService = new CommunityService(db, authService, userDao, vendorDao, loanDao, repaymentDao,
                transactionDao, uploadDao, incomeDao, vouchDao, vendorService, loanService, scoreService);
        loanService.setLenderNames(id -> {
            try {
                return userDao.findById(id).map(com.trustlend.model.User::getDisplayName).orElse("Lender #" + id);
            } catch (DataAccessException e) {
                return "Lender #" + id;
            }
        });
    }

    /**
     * Opens the shared community database (data/community.db). On the very first start it is filled
     * with the demo community and the demo accounts (HQ, lenders, borrowers).
     */
    public static AppContext open() throws DataAccessException {
        AppConfig cfg = AppConfig.getInstance();
        Path file = cfg.getPath("db.path");
        DatabaseManager db = DatabaseManager.forFile(file);
        db.initSchema();
        AppContext ctx = new AppContext(db, EngineSettings.fromConfig(cfg), file);
        ctx.startUp(true);
        return ctx;
    }

    /** Optionally seeds the demo community into an empty database, then refreshes overdue loans and scores. */
    public void startUp(boolean seedDemo) throws DataAccessException {
        LocalDate today = LocalDate.now();
        if (seedDemo && isEmpty()) {
            seedDemo(today);
            DemoAccounts.create(this);
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

    /**
     * HQ only: wipes the whole community (every vendor and every borrower/lender account; HQ accounts stay)
     * and loads the demo community and demo accounts again.
     */
    public void resetToDemo() throws DataAccessException {
        clearWorkspace();
        db.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM users WHERE role IN ('BORROWER', 'LENDER')");
            }
            return null;
        });
        seedDemo(LocalDate.now());
        DemoAccounts.create(this);
        loanService.refreshOverdue(LocalDate.now());
        scoreService.recalculateAll(LocalDate.now());
    }

    /** Deletes every vendor; foreign keys with ON DELETE CASCADE remove all their related rows. */
    public void clearWorkspace() throws DataAccessException {
        db.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM vendors");
                st.executeUpdate("DELETE FROM sqlite_sequence WHERE name <> 'users'");   // ids start from 1 again
            }
            return null;
        });
        scoreService.recalculateAll(LocalDate.now());
        LOG.info("Workspace cleared");
    }

    /** The database file on disk (null for in-memory databases). */
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
    public AuthService auth() { return authService; }
    public CommunityService community() { return communityService; }
    UserDao users() { return userDao; }
    LoanDao loanDao() { return loanDao; }
    RepaymentDao repaymentDao() { return repaymentDao; }

    @Override
    public void close() {
        db.close();
    }
}
