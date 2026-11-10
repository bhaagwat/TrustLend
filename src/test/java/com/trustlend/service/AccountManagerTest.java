package com.trustlend.service;

import com.trustlend.db.DatabaseManager;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Multi-user accounts: sign-up rules, private workspaces, demo data and the old-database migration. */
class AccountManagerTest {

    private Path dir;
    private AccountManager accounts;

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("trustlend-accounts");
        accounts = new AccountManager(DatabaseManager.forFile(dir.resolve("accounts.db")), dir.resolve("workspaces"));
    }

    @AfterEach
    void tearDown() throws IOException {
        accounts.close();
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void signUpThenSignInIgnoringUsernameCase() throws Exception {
        User created = accounts.auth().signUp("Priya Sharma", "Priya_S", "lend2026", "lend2026");
        assertEquals("priya_s", created.getUsername());
        accounts.auth().logout();
        User again = accounts.auth().login("PRIYA_S", "lend2026");
        assertEquals(created.getId(), again.getId());
        assertThrows(ValidationException.class, () -> accounts.auth().login("priya_s", "wrong-pass1"));
    }

    @Test
    void signUpValidationNamesTheField() throws Exception {
        assertEquals("name", assertThrows(ValidationException.class,
                () -> accounts.auth().signUp("", "priya", "lend2026", "lend2026")).getField());
        assertEquals("username", assertThrows(ValidationException.class,
                () -> accounts.auth().signUp("Priya", "p!", "lend2026", "lend2026")).getField());
        assertEquals("password", assertThrows(ValidationException.class,
                () -> accounts.auth().signUp("Priya", "priya", "short", "short")).getField());
        assertEquals("password", assertThrows(ValidationException.class,
                () -> accounts.auth().signUp("Priya", "priya", "lettersonly", "lettersonly")).getField());
        assertEquals("confirm", assertThrows(ValidationException.class,
                () -> accounts.auth().signUp("Priya", "priya", "lend2026", "lend2027")).getField());
        accounts.auth().signUp("Priya", "priya", "lend2026", "lend2026");
        assertEquals("username", assertThrows(ValidationException.class,
                () -> accounts.auth().signUp("Other Priya", "priya", "lend2026", "lend2026")).getField());
    }

    @Test
    void newAccountsStartEmptyAndNeverSeeEachOther() throws Exception {
        User teacher = accounts.auth().signUp("Dr Mehta", "teacher", "demo1234", "demo1234");
        User student = accounts.auth().signUp("Student", "student", "demo1234", "demo1234");

        try (AppContext a = accounts.openWorkspace(teacher)) {
            assertTrue(a.isEmpty());
            assertTrue(a.loadDemoData() >= 20);
            assertFalse(a.isEmpty());
            assertFalse(a.loans().listLoans().isEmpty());
        }
        try (AppContext b = accounts.openWorkspace(student)) {
            assertTrue(b.isEmpty());                       // the teacher's demo data is not visible
            assertEquals(0, b.vendors().findAll().size());
        }
        try (AppContext a = accounts.openWorkspace(teacher)) {
            assertFalse(a.isEmpty());                      // and it is still there at the next login
        }
        assertTrue(Files.exists(accounts.workspaceFile(teacher)));
        assertTrue(Files.exists(accounts.workspaceFile(student)));
    }

    @Test
    void demoDataOnlyIntoEmptyWorkspaceAndClearStartsOver() throws Exception {
        User u = accounts.auth().signUp("Priya", "priya", "lend2026", "lend2026");
        try (AppContext ctx = accounts.openWorkspace(u)) {
            long n = ctx.loadDemoData();
            assertThrows(DataAccessException.class, ctx::loadDemoData);
            ctx.clearWorkspace();
            assertTrue(ctx.isEmpty());
            assertTrue(ctx.transactions().all().isEmpty());
            assertTrue(ctx.loans().listLoans().isEmpty());
            assertEquals(n, ctx.loadDemoData());          // can be loaded again after clearing
        }
    }

    @Test
    void oldSingleUserDatabaseBecomesTheAdminAccount() throws Exception {
        Path legacy = dir.resolve("trustlend.db");
        try (DatabaseManager old = DatabaseManager.forFile(legacy); AppContext ctx = new AppContext(old,
                com.trustlend.engine.EngineSettings.defaults())) {
            old.initSchema();
            ctx.loadDemoData();
        }
        accounts.adoptLegacyDatabase(legacy);
        assertTrue(accounts.legacyMigrated());
        assertFalse(Files.exists(legacy));
        User admin = accounts.auth().login(AccountManager.LEGACY_USERNAME, AccountManager.LEGACY_PASSWORD);
        try (AppContext ctx = accounts.openWorkspace(admin)) {
            assertFalse(ctx.isEmpty());                    // the old vendors are all there
        }
    }
}
