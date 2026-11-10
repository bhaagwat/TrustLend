package com.trustlend.service;

import com.trustlend.config.AppConfig;
import com.trustlend.dao.jdbc.JdbcUserDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.engine.EngineSettings;
import com.trustlend.exception.DataAccessException;
import com.trustlend.model.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Multi-user support ("database per tenant"):
 * <ul>
 *   <li>{@code data/accounts.db} holds the login accounts, shared by everyone on this computer;</li>
 *   <li>every account gets its own workspace file {@code data/workspaces/user-<id>.db} with its own
 *       vendors, loans, vouches and transactions, so users never see each other's data and a new
 *       account always starts empty.</li>
 * </ul>
 */
public final class AccountManager implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AccountManager.class.getName());

    /** Account created for a database left by an older single-user version. */
    public static final String LEGACY_USERNAME = "admin";
    public static final String LEGACY_PASSWORD = "admin123";

    private final DatabaseManager accountsDb;
    private final AuthService auth;
    private final Path workspaceDir;
    private boolean legacyMigrated;

    public AccountManager(DatabaseManager accountsDb, Path workspaceDir) throws DataAccessException {
        this.accountsDb = accountsDb;
        this.workspaceDir = workspaceDir;
        accountsDb.initSchema("/db/accounts.sql");
        this.auth = new AuthService(new JdbcUserDao(accountsDb));
    }

    /** Opens the configured accounts database and adopts an old single-user database if present. */
    public static AccountManager open() throws DataAccessException {
        AppConfig cfg = AppConfig.getInstance();
        AccountManager m = new AccountManager(DatabaseManager.forFile(cfg.getPath("accounts.path")), cfg.getPath("workspace.dir"));
        m.adoptLegacyDatabase(cfg.getPath("legacy.db.path"));
        return m;
    }

    public AuthService auth() {
        return auth;
    }

    /** True if an old data/trustlend.db was turned into the admin account during this start. */
    public boolean legacyMigrated() {
        return legacyMigrated;
    }

    public Path workspaceFile(User user) {
        return workspaceDir.resolve("user-" + user.getId() + ".db");
    }

    /** Opens (creating if needed) the user's private workspace and brings scores up to date. */
    public AppContext openWorkspace(User user) throws DataAccessException {
        Path file = workspaceFile(user);
        DatabaseManager db = DatabaseManager.forFile(file);
        db.initSchema();
        AppContext ctx = new AppContext(db, EngineSettings.fromConfig(AppConfig.getInstance()), file);
        ctx.startUp(false);
        LOG.info("Workspace " + file.getFileName() + " opened for '" + user.getUsername() + "'");
        return ctx;
    }

    /**
     * Version 1 kept everything in one data/trustlend.db. On the first start of this version that file
     * becomes the workspace of a new "admin" account, so no existing data is lost.
     */
    void adoptLegacyDatabase(Path legacy) throws DataAccessException {
        if (legacy == null || !Files.exists(legacy) || auth.findAccount(LEGACY_USERNAME)) return;
        User admin = auth.createAccount(LEGACY_USERNAME, LEGACY_PASSWORD, "Loan Officer", User.Role.ADMIN);
        Path target = workspaceFile(admin);
        try {
            Files.createDirectories(workspaceDir);
            for (String suffix : new String[]{"", "-wal", "-shm"}) {
                Path from = Path.of(legacy + suffix);
                if (Files.exists(from)) Files.move(from, Path.of(target + suffix), StandardCopyOption.REPLACE_EXISTING);
            }
            legacyMigrated = true;
            LOG.info("Old database moved to the '" + LEGACY_USERNAME + "' account (" + target.getFileName() + ")");
        } catch (IOException e) {
            throw new DataAccessException("Could not move " + legacy + " into the admin workspace", e);
        }
    }

    @Override
    public void close() {
        accountsDb.close();
    }
}
