package com.trustlend.service;

import com.trustlend.config.AppConfig;
import com.trustlend.dao.IncomeDao;
import com.trustlend.dao.LoanDao;
import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.UploadDao;
import com.trustlend.dao.UserDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.dao.VouchDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.EntityNotFoundException;
import com.trustlend.exception.ValidationException;
import com.trustlend.io.StatementImporter;
import com.trustlend.model.IncomeDeclaration;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.Transaction;
import com.trustlend.model.Upload;
import com.trustlend.model.User;
import com.trustlend.model.Vendor;
import com.trustlend.model.VendorCategory;
import com.trustlend.util.Formats;
import com.trustlend.util.Validators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The community credit bureau: everything that lets borrowers and lenders feed TrustLend
 * themselves instead of a loan officer typing it all in.
 * <ul>
 *   <li><b>Lenders report, borrowers confirm</b> — a repayment a lender reports counts half until the
 *       borrower confirms it; a disputed one counts nothing until HQ decides.</li>
 *   <li><b>Borrowers upload proof</b> — UPI / sales statements are checked for tampering and turned into
 *       daily sales; income can be declared and later verified.</li>
 *   <li><b>HQ oversees</b> — a verification queue, and a credibility rating for every lender.</li>
 * </ul>
 */
public class CommunityService {

    private static final Logger LOG = Logger.getLogger(CommunityService.class.getName());

    private final DatabaseManager db;
    private final AuthService auth;
    private final UserDao userDao;
    private final VendorDao vendorDao;
    private final LoanDao loanDao;
    private final RepaymentDao repaymentDao;
    private final TransactionDao transactionDao;
    private final UploadDao uploadDao;
    private final IncomeDao incomeDao;
    private final VouchDao vouchDao;
    private final VendorService vendors;
    private final LoanService loans;
    private final ScoreService scores;

    CommunityService(DatabaseManager db, AuthService auth, UserDao userDao, VendorDao vendorDao, LoanDao loanDao,
                     RepaymentDao repaymentDao, TransactionDao transactionDao, UploadDao uploadDao, IncomeDao incomeDao,
                     VouchDao vouchDao, VendorService vendors, LoanService loans, ScoreService scores) {
        this.db = db;
        this.auth = auth;
        this.userDao = userDao;
        this.vendorDao = vendorDao;
        this.loanDao = loanDao;
        this.repaymentDao = repaymentDao;
        this.transactionDao = transactionDao;
        this.uploadDao = uploadDao;
        this.incomeDao = incomeDao;
        this.vouchDao = vouchDao;
        this.vendors = vendors;
        this.loans = loans;
        this.scores = scores;
    }

    private void rescore() throws DataAccessException {
        loans.refreshOverdue(LocalDate.now());
        scores.recalculateAll(LocalDate.now());
    }

    /** "TL-1007": the id a borrower gives a lender, printed on their certificate. */
    public static String trustId(long vendorId) {
        return "TL-" + (1000 + vendorId);
    }

    /** Finds a borrower by TrustLend ID ("TL-1007" or "1007") or phone number. */
    public java.util.Optional<Vendor> findBorrower(String idOrPhone) throws DataAccessException {
        String t = idOrPhone == null ? "" : idOrPhone.trim().toUpperCase().replace("TL-", "").replace(" ", "");
        if (t.matches("\\d{4,5}")) {
            long id = Long.parseLong(t) - 1000;
            java.util.Optional<Vendor> v = vendorDao.findById(id);
            if (v.isPresent()) return v;
        }
        String phone = idOrPhone == null ? "" : idOrPhone.replaceAll("[^0-9]", "");
        if (phone.length() >= 10) return vendorDao.findByPhone(phone.substring(phone.length() - 10));
        return java.util.Optional.empty();
    }

    // ================================================================== sign-up

    /** A street vendor signs up: creates their vendor profile and their borrower login together. */
    public User signUpBorrower(String fullName, String phone, VendorCategory category, String market, String city,
                               int years, String username, String password, String confirm)
            throws ValidationException, DataAccessException, EntityNotFoundException {
        String user = auth.checkNewAccount(username, password, confirm);
        Vendor v = vendors.save(null, fullName, phone, category, market, city, years, LocalDate.now());
        User u = auth.create(user, password, v.getName(), User.Role.BORROWER, v.getId(), null, null);
        rescore();
        auth.setCurrentUser(u);
        return u;
    }

    /** A moneylender, self-help group, wholesaler or microfinance company signs up. */
    public User signUpLender(String fullName, String organisation, String lenderType, String username,
                             String password, String confirm) throws ValidationException, DataAccessException {
        String name = Validators.requireName("name", fullName);
        String org = Validators.requireText("organisation", organisation);
        if (lenderType == null || lenderType.isBlank()) throw new ValidationException("type", "Choose what kind of lender you are");
        String user = auth.checkNewAccount(username, password, confirm);
        User u = auth.create(user, password, name, User.Role.LENDER, 0, org, lenderType);
        auth.setCurrentUser(u);
        return u;
    }

    // ================================================================== lenders report

    /** A lender gives a loan through TrustLend (checked against the borrower's score, like any loan). */
    public Loan lendAsLender(User lender, long vendorId, double amount, int weeks, LocalDate issueDate) throws Exception {
        requireLender(lender);
        Loan loan = loans.issueLoan(vendorId, amount, weeks, issueDate, lender.getId());
        rescore();
        return loan;
    }

    /** A lender reports an instalment received. It counts half until the borrower confirms it. */
    public Repayment reportRepayment(User lender, long loanId, double amount, LocalDate paidDate) throws Exception {
        requireLender(lender);
        Loan loan = loanDao.findById(loanId).orElseThrow(() -> new EntityNotFoundException("Loan", loanId));
        if (loan.getLenderId() != lender.getId()) {
            throw new ValidationException("loan", "Loan #" + loanId + " was not given by you");
        }
        Repayment r = loans.recordRepayment(loanId, amount, paidDate, lender.getId());
        rescore();
        return r;
    }

    /**
     * A lender reports a loan given outside TrustLend, e.g. "I lent Ramesh ₹5,000 in March and he paid it
     * all back on time". It is stored as a repaid loan with one repayment awaiting the borrower's confirmation.
     */
    public Loan reportPastLoan(User lender, long vendorId, double principal, double ratePercent, int weeks,
                               LocalDate issueDate, LocalDate repaidOn) throws Exception {
        requireLender(lender);
        Vendor v = vendorDao.findById(vendorId).orElseThrow(() -> new EntityNotFoundException("Vendor", vendorId));
        Validators.requirePositive("amount", principal);
        Validators.requireRange("tenure (weeks)", weeks, 1, 104);
        if (ratePercent < 0 || ratePercent > 120) throw new ValidationException("interest", "Interest must be 0-120% a year");
        Validators.requireNotFuture("issue date", issueDate);
        Validators.requireNotFuture("repaid on", repaidOn);
        if (repaidOn.isBefore(issueDate)) throw new ValidationException("repaid on", "Repayment cannot be before the loan");
        LocalDate due = issueDate.plusWeeks(weeks);
        Loan loan = new Loan(0, vendorId, principal, ratePercent, weeks, issueDate, due, LoanStatus.REPAID, v.getCurrentScore());
        loan.setLenderId(lender.getId());
        loan.setOrigin(Loan.Origin.REPORTED);
        db.transaction(() -> {
            loanDao.save(loan);
            Repayment r = Repayment.of(loan.getId(), loan.getTotalPayable(), due, repaidOn);
            r.setReportedBy(lender.getId());
            r.setStatus(Repayment.Verification.PENDING);
            r.setNote("Outside loan reported by " + lender.getDisplayName());
            repaymentDao.save(r);
            return loan;
        });
        LOG.info(lender.getUsername() + " reported a past loan of " + Formats.inr(principal) + " to " + v.getName());
        rescore();
        return loan;
    }

    private static void requireLender(User u) throws ValidationException {
        if (u == null || !u.isLender()) throw new ValidationException("role", "Only lenders can do this");
    }

    // ================================================================== borrowers confirm

    /** A repayment that a lender reported and the borrower has not confirmed yet. */
    public record Claim(Repayment repayment, Loan loan, String lenderName) { }

    public List<Claim> pendingClaims(long vendorId) throws DataAccessException {
        return claims(repaymentDao.findByStatusForVendor(vendorId, Repayment.Verification.PENDING));
    }

    private List<Claim> claims(List<Repayment> reps) throws DataAccessException {
        List<Claim> out = new ArrayList<>();
        Map<Long, String> names = lenderNames();
        for (Repayment r : reps) {
            Loan l = loanDao.findById(r.getLoanId()).orElse(null);
            if (l == null) continue;
            l.setVendorName(vendorDao.findById(l.getVendorId()).map(Vendor::getName).orElse("?"));
            out.add(new Claim(r, l, names.getOrDefault(r.getReportedBy(), "TrustLend HQ")));
        }
        return out;
    }

    public void confirm(User borrower, long repaymentId) throws Exception {
        Repayment r = ownedPending(borrower, repaymentId);
        repaymentDao.updateStatus(r.getId(), Repayment.Verification.CONFIRMED, r.getNote());
        LOG.info(borrower.getUsername() + " confirmed repayment #" + repaymentId);
        rescore();
    }

    public void dispute(User borrower, long repaymentId, String reason) throws Exception {
        Repayment r = ownedPending(borrower, repaymentId);
        String why = reason == null || reason.isBlank() ? "Borrower says this payment did not happen" : reason.trim();
        repaymentDao.updateStatus(r.getId(), Repayment.Verification.DISPUTED, why);
        LOG.warning(borrower.getUsername() + " disputed repayment #" + repaymentId + ": " + why);
        rescore();
    }

    private Repayment ownedPending(User borrower, long repaymentId) throws Exception {
        if (borrower == null || !borrower.isBorrower()) throw new ValidationException("role", "Only the borrower can confirm");
        Repayment r = repaymentDao.findById(repaymentId).orElseThrow(() -> new EntityNotFoundException("Repayment", repaymentId));
        Loan l = loanDao.findById(r.getLoanId()).orElseThrow(() -> new EntityNotFoundException("Loan", r.getLoanId()));
        if (l.getVendorId() != borrower.getVendorId()) throw new ValidationException("repayment", "This is not your loan");
        if (r.getStatus() != Repayment.Verification.PENDING) throw new ValidationException("repayment", "Already " + r.getStatus().getLabel().toLowerCase());
        return r;
    }

    // ================================================================== borrowers upload proof

    /** What happened to an uploaded statement. */
    public record UploadOutcome(Upload upload, StatementImporter.Result analysis, int daysAdded, int scoreBefore, int scoreAfter) { }

    public UploadOutcome uploadStatement(long vendorId, Path file) throws Exception {
        return uploadStatement(vendorId, file.getFileName().toString(), StatementImporter.analyse(file, LocalDate.now()));
    }

    public UploadOutcome uploadStatement(long vendorId, String fileName, List<String> lines) throws Exception {
        return uploadStatement(vendorId, fileName, StatementImporter.analyse(lines, LocalDate.now()));
    }

    private UploadOutcome uploadStatement(long vendorId, String fileName, StatementImporter.Result a) throws Exception {
        Vendor v = vendorDao.findById(vendorId).orElseThrow(() -> new EntityNotFoundException("Vendor", vendorId));
        int before = scores.scoreValue(vendorId);

        // days already covered by an earlier upload are skipped, so the same file cannot be counted twice
        Set<LocalDate> covered = new HashSet<>();
        for (Transaction t : transactionDao.findByVendor(vendorId)) {
            if (t.getSource() != Transaction.Source.RECORDED && t.getType() == Transaction.Type.SALE) covered.add(t.getDate());
        }
        List<StatementImporter.DailySale> fresh = a.days().stream().filter(d -> !covered.contains(d.date())).toList();
        List<String> issues = new ArrayList<>(a.issues());
        Upload.Status status = a.status();
        int quality = a.quality();
        if (!a.days().isEmpty() && fresh.isEmpty()) {
            issues.add("Every day in this file was already uploaded before");
            status = Upload.Status.REJECTED;
            quality = Math.min(quality, 20);
        }
        Upload upload = new Upload(0, vendorId, fileName, LocalDate.now(), a.rows(), fresh.size(),
                fresh.stream().mapToDouble(StatementImporter.DailySale::amount).sum(), quality, status, String.join("\n", issues));
        Upload.Status finalStatus = status;
        db.transaction(() -> {
            uploadDao.save(upload);
            if (finalStatus != Upload.Status.REJECTED) {
                List<Transaction> rows = new ArrayList<>();
                for (StatementImporter.DailySale d : fresh) {
                    Transaction t = new Transaction(vendorId, d.amount(), Transaction.Type.SALE, d.date(),
                            "UPI sales: " + d.payments() + " payment(s) · " + fileName);
                    t.setSource(finalStatus == Upload.Status.ACCEPTED ? Transaction.Source.UPLOADED : Transaction.Source.HELD);
                    t.setUploadId(upload.getId());
                    rows.add(t);
                }
                if (!rows.isEmpty()) transactionDao.insertBatch(rows);
            }
            return upload;
        });
        LOG.info(v.getName() + " uploaded " + fileName + ": quality " + quality + " → " + status);
        rescore();
        return new UploadOutcome(upload, a, status == Upload.Status.REJECTED ? 0 : fresh.size(), before, scores.scoreValue(vendorId));
    }

    // ================================================================== income

    public IncomeDeclaration declareIncome(long vendorId, double monthly, IncomeDeclaration.Kind kind, Path proof)
            throws Exception {
        Validators.requirePositive("monthly income", monthly);
        if (monthly > 10_00_000) throw new ValidationException("monthly income", "Please enter a monthly amount");
        if (kind == null) throw new ValidationException("kind", "Choose the type of income");
        String stored = null;
        if (proof != null) {
            Path dir = AppConfig.getInstance().getPath("proofs.dir");
            Files.createDirectories(dir);
            Path target = dir.resolve(vendorId + "-" + System.currentTimeMillis() + "-" + proof.getFileName().toString().replaceAll("[^\\w.-]", "_"));
            Files.copy(proof, target, StandardCopyOption.REPLACE_EXISTING);
            stored = target.toString();
        }
        IncomeDeclaration d = new IncomeDeclaration(0, vendorId, monthly, kind, stored, LocalDate.now(),
                IncomeDeclaration.Status.DECLARED, 0);
        incomeDao.save(d);
        rescore();
        return d;
    }

    public List<IncomeDeclaration> incomesOf(long vendorId) throws DataAccessException {
        return incomeDao.findByVendor(vendorId);
    }

    public List<Upload> uploadsOf(long vendorId) throws DataAccessException {
        return uploadDao.findByVendor(vendorId);
    }

    // ================================================================== HQ / lender verification

    /** Everything waiting for a human decision. */
    public record Queue(List<Claim> disputes, List<Upload> heldUploads, List<IncomeDeclaration> incomes) {
        public int size() {
            return disputes.size() + heldUploads.size() + incomes.size();
        }
    }

    public Queue queue() throws DataAccessException {
        return new Queue(claims(repaymentDao.findByStatus(Repayment.Verification.DISPUTED)),
                uploadDao.findByStatus(Upload.Status.REVIEW),
                incomeDao.findByStatus(IncomeDeclaration.Status.DECLARED));
    }

    /** HQ decides a dispute: {@code paymentHappened} → confirmed, otherwise rejected (and it hurts the lender's credibility). */
    public void resolveDispute(User hq, long repaymentId, boolean paymentHappened) throws Exception {
        requireHq(hq);
        Repayment r = repaymentDao.findById(repaymentId).orElseThrow(() -> new EntityNotFoundException("Repayment", repaymentId));
        repaymentDao.updateStatus(repaymentId, paymentHappened ? Repayment.Verification.CONFIRMED : Repayment.Verification.REJECTED,
                (r.getNote() == null ? "" : r.getNote() + " · ") + (paymentHappened ? "HQ: payment verified" : "HQ: report rejected"));
        rescore();
    }

    public void approveUpload(User hq, long uploadId) throws Exception {
        requireHq(hq);
        uploadDao.updateStatus(uploadId, Upload.Status.ACCEPTED);
        transactionDao.setSourceForUpload(uploadId, Transaction.Source.UPLOADED);
        rescore();
    }

    public void rejectUpload(User hq, long uploadId) throws Exception {
        requireHq(hq);
        db.transaction(() -> {
            uploadDao.updateStatus(uploadId, Upload.Status.REJECTED);
            for (Transaction t : transactionDao.findAll()) {
                if (t.getUploadId() == uploadId) transactionDao.delete(t.getId());
            }
            return null;
        });
        rescore();
    }

    /** A lender or HQ checked the salary slip / bank statement: the income now counts fully. */
    public void verifyIncome(User verifier, long incomeId, boolean genuine) throws Exception {
        if (verifier == null || verifier.isBorrower()) throw new ValidationException("role", "Only a lender or HQ can verify income");
        IncomeDeclaration d = incomeDao.findById(incomeId).orElseThrow(() -> new EntityNotFoundException("Income", incomeId));
        d.setStatus(genuine ? IncomeDeclaration.Status.VERIFIED : IncomeDeclaration.Status.REJECTED);
        d.setVerifiedBy(verifier.getId());
        incomeDao.save(d);
        rescore();
    }

    private static void requireHq(User u) throws ValidationException {
        if (u == null || !u.isAdmin()) throw new ValidationException("role", "Only TrustLend HQ can do this");
    }

    // ================================================================== evidence & credibility

    /** What a score is built from, and how much each piece counts. Shown under every score. */
    public record Evidence(int uploadedSaleDays, int recordedSaleDays, int heldSaleDays, int confirmedRepayments,
                           int pendingRepayments, int disputedRepayments, int platformLoans, int reportedLoans,
                           int vouches, double verifiedIncome, double declaredIncome, int uploads) { }

    public Evidence evidence(long vendorId) throws DataAccessException {
        Set<LocalDate> uploaded = new HashSet<>(), recorded = new HashSet<>(), held = new HashSet<>();
        for (Transaction t : transactionDao.findByVendor(vendorId)) {
            if (t.getType() != Transaction.Type.SALE) continue;
            switch (t.getSource()) {
                case UPLOADED -> uploaded.add(t.getDate());
                case HELD -> held.add(t.getDate());
                default -> recorded.add(t.getDate());
            }
        }
        int confirmed = 0, pending = 0, disputed = 0;
        for (Repayment r : repaymentDao.findByVendor(vendorId)) {
            switch (r.getStatus()) {
                case CONFIRMED -> confirmed++;
                case PENDING -> pending++;
                default -> disputed++;
            }
        }
        int platform = 0, reported = 0;
        for (Loan l : loanDao.findByVendor(vendorId)) {
            if (l.getOrigin() == Loan.Origin.REPORTED) reported++;
            else platform++;
        }
        double verified = 0, declared = 0;
        for (IncomeDeclaration d : incomeDao.findByVendor(vendorId)) {
            if (d.getStatus() == IncomeDeclaration.Status.VERIFIED && verified == 0) verified = d.getMonthlyAmount();
            if (d.getStatus() == IncomeDeclaration.Status.DECLARED && declared == 0) declared = d.getMonthlyAmount();
        }
        int vouches = (int) vouchDao.findAll().stream().filter(v -> v.getVoucheeId() == vendorId).count();
        return new Evidence(uploaded.size(), recorded.size(), held.size(), confirmed, pending, disputed, platform,
                reported, vouches, verified, declared, uploadDao.findByVendor(vendorId).size());
    }

    /** How a lender is doing, and how much their reports can be trusted. */
    public record LenderStats(User lender, int loans, int borrowers, double lent, double outstanding, int reports,
                              int confirmed, int pending, int disputed, double credibility) {
        /** 0-100, shown as a percentage. */
        public int credibilityPercent() {
            return (int) Math.round(credibility * 100);
        }
    }

    /**
     * Credibility = (confirmed + 1) / (confirmed + disputed + 2): a Bayesian average, so a new lender
     * starts at 50% and every report a borrower confirms raises it, every one they dispute lowers it.
     */
    public LenderStats lenderStats(User lender) throws DataAccessException {
        List<Loan> mine = loans.loansBy(lender.getId());
        Set<Long> borrowers = new HashSet<>();
        double lent = 0, outstanding = 0;
        for (Loan l : mine) {
            borrowers.add(l.getVendorId());
            lent += l.getPrincipal();
            if (l.getStatus().isOpen()) outstanding += l.getOutstanding();
        }
        int confirmed = 0, pending = 0, disputed = 0;
        List<Repayment> reports = repaymentDao.findReportedBy(lender.getId());
        for (Repayment r : reports) {
            switch (r.getStatus()) {
                case CONFIRMED -> confirmed++;
                case PENDING -> pending++;
                default -> disputed++;
            }
        }
        double cred = (confirmed + 1.0) / (confirmed + disputed + 2.0);
        return new LenderStats(lender, mine.size(), borrowers.size(), lent, outstanding, reports.size(), confirmed,
                pending, disputed, cred);
    }

    public List<User> members() throws DataAccessException {
        return userDao.findAll();
    }

    public List<User> lenders() throws DataAccessException {
        return userDao.findByRole(User.Role.LENDER);
    }

    public java.util.Optional<User> accountOf(long vendorId) throws DataAccessException {
        return userDao.findByVendor(vendorId);
    }

    /** User id → name to show (organisation for lenders). */
    public Map<Long, String> lenderNames() throws DataAccessException {
        Map<Long, String> names = new HashMap<>();
        for (User u : userDao.findAll()) names.put(u.getId(), u.getDisplayName());
        return names;
    }

    /** Repayments a lender reported that are still waiting for the borrower. */
    public List<Claim> awaitingConfirmation(User lender) throws DataAccessException {
        return claims(repaymentDao.findReportedBy(lender.getId()).stream()
                .filter(r -> r.getStatus() == Repayment.Verification.PENDING).toList());
    }

    /** Copies a proof file path for opening (used by HQ to look at a salary slip). */
    public static Path proofPath(IncomeDeclaration d) {
        return d.getProofFile() == null ? null : Path.of(d.getProofFile());
    }

    /** Splits the text of a statement sent from the phone page into lines. */
    public static List<String> lines(String text) throws IOException {
        if (text == null || text.isBlank()) throw new IOException("The file is empty");
        return text.lines().filter(l -> !l.isBlank()).toList();
    }
}
