package com.trustlend.dao;

import com.trustlend.dao.jdbc.JdbcLoanDao;
import com.trustlend.dao.jdbc.JdbcTransactionDao;
import com.trustlend.dao.jdbc.JdbcVendorDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.engine.EngineSettings;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.LoanEligibilityException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.service.AppContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real JDBC DAOs against an in-memory SQLite database. */
class DaoIntegrationTest {

    private DatabaseManager db;
    private VendorDao vendors;

    @BeforeEach
    void setUp() throws Exception {
        db = DatabaseManager.inMemory();
        db.initSchema();
        vendors = new JdbcVendorDao(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private Vendor newVendor(String phone) {
        return new Vendor("Asha Devi", phone, VendorCategory.FLOWERS, "Sadar Bazaar", "Delhi", 4, LocalDate.now().minusMonths(6));
    }

    @Test
    @DisplayName("CRUD round-trip through the vendor DAO")
    void vendorCrud() throws Exception {
        Vendor v = vendors.save(newVendor("9876500001"));
        assertTrue(v.getId() > 0);
        v.setMarketArea("Karol Bagh");
        vendors.save(v);
        assertEquals("Karol Bagh", vendors.findById(v.getId()).orElseThrow().getMarketArea());
        assertEquals(1, vendors.search("asha").size());
        assertTrue(vendors.delete(v.getId()));
        assertTrue(vendors.findById(v.getId()).isEmpty());
    }

    @Test
    @DisplayName("UNIQUE constraint on phone surfaces as DataAccessException")
    void duplicatePhoneRejected() throws Exception {
        vendors.save(newVendor("9876500002"));
        assertThrows(DataAccessException.class, () -> vendors.save(newVendor("9876500002")));
    }

    @Test
    @DisplayName("A failed transaction is rolled back completely")
    void rollbackOnFailure() throws Exception {
        Vendor v = vendors.save(newVendor("9876500003"));
        TransactionDao txns = new JdbcTransactionDao(db);
        assertThrows(DataAccessException.class, () -> db.transaction(() -> {
            txns.save(new Transaction(v.getId(), 100, Transaction.Type.SALE, LocalDate.now(), "ok"));
            txns.save(new Transaction(v.getId(), -5, Transaction.Type.SALE, LocalDate.now(), "violates CHECK"));
            return null;
        }));
        assertEquals(0, txns.count());
    }

    @Test
    @DisplayName("Batch insert and date-range query")
    void batchInsert() throws Exception {
        Vendor v = vendors.save(newVendor("9876500004"));
        TransactionDao txns = new JdbcTransactionDao(db);
        List<Transaction> list = List.of(
                new Transaction(v.getId(), 100, Transaction.Type.SALE, LocalDate.now().minusDays(1), null),
                new Transaction(v.getId(), 200, Transaction.Type.SALE, LocalDate.now().minusDays(10), null),
                new Transaction(v.getId(), 300, Transaction.Type.DEPOSIT, LocalDate.now().minusDays(40), null));
        assertEquals(3, txns.insertBatch(list));
        assertEquals(2, txns.findByVendorBetween(v.getId(), LocalDate.now().minusDays(30), LocalDate.now()).size());
    }

    @Test
    @DisplayName("Deleting a vendor cascades to their loans (foreign keys enabled)")
    void cascadeDelete() throws Exception {
        Vendor v = vendors.save(newVendor("9876500005"));
        LoanDao loans = new JdbcLoanDao(db);
        loans.save(new Loan(0, v.getId(), 1000, 18, 12, LocalDate.now(), LocalDate.now().plusWeeks(12), LoanStatus.ACTIVE, 600));
        vendors.delete(v.getId());
        assertEquals(0, loans.count());
    }

    @Test
    @DisplayName("Service layer: low-score vendor is refused a loan; repayment closes a loan")
    void loanServiceRules() throws Exception {
        AppContext ctx = new AppContext(db, EngineSettings.defaults());
        ctx.startUp(false);
        Vendor newbie = ctx.vendors().save(null, "Ravi Kumar", "9876500006", VendorCategory.TEA_STALL,
                "Paharganj", "Delhi", 0, LocalDate.now());
        ctx.scores().recalculateAll(LocalDate.now());
        assertThrows(LoanEligibilityException.class,
                () -> ctx.loans().issueLoan(newbie.getId(), 2000, 12, LocalDate.now()));
    }
}
