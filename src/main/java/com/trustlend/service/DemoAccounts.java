package com.trustlend.service;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.IncomeDeclaration;
import com.trustlend.model.Loan;
import com.trustlend.model.Repayment;
import com.trustlend.model.User;
import com.trustlend.model.Vendor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Ready-made accounts for the demo community, so every screen has something to show:
 * <ul>
 *   <li>HQ: {@code admin / admin123}</li>
 *   <li>Lenders: {@code meena_shg}, {@code sharma_wholesale}, {@code udaan_mfi} (password {@code demo123})</li>
 *   <li>Borrowers: {@code javed}, {@code ramesh}, {@code geeta}, {@code sunita}, {@code kamla}, {@code irfan}
 *       (password {@code demo123})</li>
 * </ul>
 * It also creates the situations the screens are about: repayments waiting for confirmation, a
 * dispute, a suspicious statement held for review, and income declarations.
 */
public final class DemoAccounts {

    private static final Logger LOG = Logger.getLogger(DemoAccounts.class.getName());
    public static final String PASSWORD = "demo123";
    public static final String HQ_PASSWORD = "admin123";

    /** Shown on the login page as one-click demo logins: username, role, description. */
    public static final String[][] LOGINS = {
            {"admin", "ADMIN", "TrustLend HQ — oversees everything"},
            {"meena_shg", "LENDER", "Jagriti Mahila Self-Help Group"},
            {"sharma_wholesale", "LENDER", "Sharma Wholesale Traders"},
            {"udaan_mfi", "LENDER", "Udaan Microfinance"},
            {"javed", "BORROWER", "Javed Ansari · street food, patchy record"},
            {"ramesh", "BORROWER", "Ramesh Kumar · fruit seller"},
            {"geeta", "BORROWER", "Geeta Rani · vegetable seller"},
            {"kamla", "BORROWER", "Kamla Bai · flower seller"}};

    private DemoAccounts() { }

    static void create(AppContext ctx) throws DataAccessException {
        AuthService auth = ctx.auth();
        try {
            if (!auth.exists("admin")) auth.create("admin", HQ_PASSWORD, "TrustLend HQ", User.Role.ADMIN, 0, "TrustLend HQ", null);
            User meena = auth.create("meena_shg", PASSWORD, "Meena Verma", User.Role.LENDER, 0,
                    "Jagriti Mahila Self-Help Group", "Self-help group");
            User sharma = auth.create("sharma_wholesale", PASSWORD, "Rajiv Sharma", User.Role.LENDER, 0,
                    "Sharma Wholesale Traders", "Wholesaler (stock on credit)");
            User udaan = auth.create("udaan_mfi", PASSWORD, "Anita Rao", User.Role.LENDER, 0,
                    "Udaan Microfinance", "Microfinance company");
            User[] lenders = {meena, sharma, udaan};

            // the demo loans are shared between the three lenders and the community fund
            for (Loan l : ctx.loanDao().findAll()) {
                int slot = (int) (l.getId() % 4);
                if (slot < 3) ctx.loanDao().assignLender(l.getId(), lenders[slot].getId());
            }
            // each lender's latest reported instalment is still waiting for the borrower to confirm
            for (Loan l : ctx.loanDao().findAll()) {
                if (l.getLenderId() == 0 || !l.getStatus().isOpen()) continue;
                List<Repayment> reps = ctx.repaymentDao().findByLoan(l.getId());
                if (reps.isEmpty()) continue;
                Repayment last = reps.get(reps.size() - 1);
                last.setReportedBy(l.getLenderId());
                last.setStatus(Repayment.Verification.PENDING);
                ctx.repaymentDao().save(last);
            }

            Vendor javed = byName(ctx, "Javed Ansari");
            Vendor ramesh = byName(ctx, "Ramesh Kumar");
            Vendor geeta = byName(ctx, "Geeta Rani");
            Vendor sunita = byName(ctx, "Sunita Devi");
            Vendor kamla = byName(ctx, "Kamla Bai");
            Vendor irfan = byName(ctx, "Mohammed Irfan");
            for (Object[] b : new Object[][]{{"javed", javed}, {"ramesh", ramesh}, {"geeta", geeta},
                    {"sunita", sunita}, {"kamla", kamla}, {"irfan", irfan}}) {
                Vendor v = (Vendor) b[1];
                auth.create((String) b[0], PASSWORD, v.getName(), User.Role.BORROWER, v.getId(), null, null);
            }

            LocalDate today = LocalDate.now();
            CommunityService c = ctx.community();
            // Javed (street food): the SHG lent him ₹8,000 earlier, repaid on time — waiting for his confirmation
            c.reportPastLoan(meena, javed.getId(), 8_000, 24, 10, today.minusWeeks(16), today.minusWeeks(6));
            // Ramesh: a wholesaler reports stock he took on credit and paid back
            c.reportPastLoan(sharma, ramesh.getId(), 15_000, 18, 8, today.minusWeeks(14), today.minusWeeks(6).minusDays(2));
            // Kamla disputes a report she says is false
            Loan fake = c.reportPastLoan(sharma, kamla.getId(), 12_000, 30, 6, today.minusWeeks(10), today.minusWeeks(4));
            for (Repayment r : ctx.repaymentDao().findByLoan(fake.getId())) {
                ctx.repaymentDao().updateStatus(r.getId(), Repayment.Verification.DISPUTED,
                        "Kamla Bai: \"I never took credit from Sharma Wholesale\"");
            }
            // Geeta uploaded a clean UPI statement; Sunita uploaded a suspicious one (held for HQ)
            c.uploadStatement(geeta.getId(), "geeta_phonepe_statement.csv", statement(today.minusDays(29), 28, 41, false));
            c.uploadStatement(sunita.getId(), "sunita_statement.csv", statement(today.minusDays(20), 20, 9, true));
            // income: Irfan's salary verified by Udaan, Geeta's business income waiting for verification
            IncomeDeclaration salary = c.declareIncome(irfan.getId(), 22_000, IncomeDeclaration.Kind.SALARY, null);
            c.verifyIncome(udaan, salary.getId(), true);
            c.declareIncome(geeta.getId(), 18_000, IncomeDeclaration.Kind.BUSINESS, null);
            LOG.info("Demo accounts created");
        } catch (DataAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new DataAccessException("Could not create the demo accounts: " + e.getMessage(), e);
        }
    }

    private static Vendor byName(AppContext ctx, String name) throws DataAccessException {
        return ctx.vendors().findAll().stream().filter(v -> v.getName().equals(name)).findFirst()
                .orElseThrow(() -> new DataAccessException("Demo vendor missing: " + name, null));
    }

    /**
     * A PhonePe-style statement export: Date, Transaction ID, Description, Type, Amount, Status.
     * Honest statements have many small, uneven payments; a {@code suspicious} one has round amounts
     * and a few copied rows (the same transaction id twice).
     */
    public static List<String> statement(LocalDate from, int days, long seed, boolean suspicious) {
        Random r = new Random(seed);
        List<String> lines = new ArrayList<>();
        lines.add("Date,Transaction ID,Description,Type,Amount,Status");
        String[] payers = {"Rohit", "Anjali", "Sanjay", "Pooja", "Vivek", "Neha", "Amit", "Kiran", "Rahul", "Divya",
                "Manish", "Sneha", "Arun", "Kavya", "Tarun"};
        int n = 0;
        String lastLine = null;
        for (int d = 0; d < days; d++) {
            LocalDate date = from.plusDays(d);
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY && !suspicious) continue;
            int payments = suspicious ? 2 + r.nextInt(2) : 6 + r.nextInt(9);
            for (int p = 0; p < payments; p++) {
                double amount = suspicious ? 500 * (1 + r.nextInt(4)) : 10 + r.nextInt(17) * 5 + (r.nextInt(4) == 0 ? 120 : 0);
                String id = "T" + date.toString().replace("-", "") + String.format("%06d", 100000 + (seed * 7919 + n++) % 900000);
                String line = date + "," + id + ",Received from " + payers[r.nextInt(payers.length)] + ",CREDIT,"
                        + String.format("%.2f", amount) + ",SUCCESS";
                lines.add(line);
                lastLine = line;
            }
            if (!suspicious && r.nextInt(3) == 0) {   // the occasional payment to a supplier (ignored)
                lines.add(date + ",T" + date.toString().replace("-", "") + "9" + n++ + ",Paid to Wholesale Mandi,DEBIT,"
                        + (400 + r.nextInt(600)) + ".00,SUCCESS");
            }
            if (suspicious && d % 7 == 3 && lastLine != null) lines.add(lastLine);   // copied row
        }
        return lines;
    }
}
