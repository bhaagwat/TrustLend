package com.trustlend.service;

import com.trustlend.config.AppConfig;
import com.trustlend.dao.LoanDao;
import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.ScoreHistoryDao;
import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.UserDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.dao.jdbc.JdbcLoanDao;
import com.trustlend.dao.jdbc.JdbcRepaymentDao;
import com.trustlend.dao.jdbc.JdbcScoreHistoryDao;
import com.trustlend.dao.jdbc.JdbcTransactionDao;
import com.trustlend.dao.jdbc.JdbcUserDao;
import com.trustlend.dao.jdbc.JdbcVendorDao;
import com.trustlend.dao.jdbc.JdbcVouchDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.engine.EngineSettings;
import com.trustlend.exception.DataAccessException;
import com.trustlend.io.BackupService;
import com.trustlend.io.CsvTransactionImporter;

import java.time.LocalDate;
import java.util.logging.Logger;

/**
 * Composition root: builds the object graph (DB → DAOs → services) once, by hand.
 * This is "manual dependency injection" — every class receives what it needs via its constructor.
 */
public final class AppContext implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AppContext.class.getName());

    private final DatabaseManager db;
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
    private final AuthService authService;
    private final BackupService backupService;
    private final CsvTransactionImporter importer;

    public AppContext(DatabaseManager db, EngineSettings settings) {
        this.db = db;
        vendorDao = new JdbcVendorDao(db);
        transactionDao = new JdbcTransactionDao(db);
        loanDao = new JdbcLoanDao(db);
        repaymentDao = new JdbcRepaymentDao(db);
        vouchDao = new JdbcVouchDao(db);
        ScoreHistoryDao historyDao = new JdbcScoreHistoryDao(db);
        UserDao userDao = new JdbcUserDao(db);

        scoreService = new ScoreService(vendorDao, transactionDao, loanDao, repaymentDao, vouchDao, historyDao, settings);
        vendorService = new VendorService(vendorDao, scoreService);
        loanService = new LoanService(db, loanDao, repaymentDao, transactionDao, vendorDao, scoreService);
        vouchService = new VouchService(vouchDao, vendorDao, scoreService);
        transactionService = new TransactionService(transactionDao, vendorDao);
        analyticsService = new AnalyticsService(vendorService, loanService, repaymentDao, vouchDao);
        authService = new AuthService(userDao);
        backupService = new BackupService(db, vendorDao, transactionDao, loanDao, repaymentDao, vouchDao);
        importer = new CsvTransactionImporter(vendorDao);
    }

    /** Opens the configured database file and prepares everything for the UI. */
    public static AppContext bootstrap() throws DataAccessException {
        AppConfig config = AppConfig.getInstance();
        DatabaseManager db = DatabaseManager.forFile(config.getPath("db.path"));
        db.initSchema();
        AppContext ctx = new AppContext(db, EngineSettings.fromConfig(config));
        ctx.startUp(config.getBoolean("db.seed.demo"));
        return ctx;
    }

    /** Creates the admin, seeds demo data on an empty DB, refreshes overdue loans and scores. */
    public void startUp(boolean seedDemo) throws DataAccessException {
        LocalDate today = LocalDate.now();
        authService.ensureDefaultAdmin();
        if (seedDemo && vendorDao.count() == 0) {
            LOG.info("Empty database: seeding demo community");
            new DemoDataSeeder(db, vendorDao, transactionDao, loanDao, repaymentDao, vouchDao).seed(today);
            scoreService.backfillHistory(today, 12, 2);
        }
        loanService.refreshOverdue(today);
        scoreService.recalculateAll(today);
    }

    public DatabaseManager db() { return db; }
    public ScoreService scores() { return scoreService; }
    public VendorService vendors() { return vendorService; }
    public LoanService loans() { return loanService; }
    public VouchService vouches() { return vouchService; }
    public TransactionService transactions() { return transactionService; }
    public AnalyticsService analytics() { return analyticsService; }
    public AuthService auth() { return authService; }
    public BackupService backup() { return backupService; }
    public CsvTransactionImporter importer() { return importer; }

    @Override
    public void close() {
        db.close();
    }
}
