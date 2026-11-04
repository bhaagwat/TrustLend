package com.trustlend.service;

import com.trustlend.config.AppConfig;
import com.trustlend.dao.LoanDao;
import com.trustlend.dao.RepaymentDao;
import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.db.DatabaseManager;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.EntityNotFoundException;
import com.trustlend.exception.LoanEligibilityException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;
import com.trustlend.model.Repayment;
import com.trustlend.model.RiskBand;
import com.trustlend.model.Transaction;
import com.trustlend.model.Vendor;
import com.trustlend.util.Formats;
import com.trustlend.util.Validators;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Loan origination, repayment and status management. All multi-step writes are transactional. */
public class LoanService {

    private static final Logger LOG = Logger.getLogger(LoanService.class.getName());

    private final DatabaseManager db;
    private final LoanDao loanDao;
    private final RepaymentDao repaymentDao;
    private final TransactionDao transactionDao;
    private final VendorDao vendorDao;
    private final ScoreService scoreService;

    public LoanService(DatabaseManager db, LoanDao loanDao, RepaymentDao repaymentDao,
                       TransactionDao transactionDao, VendorDao vendorDao, ScoreService scoreService) {
        this.db = db;
        this.loanDao = loanDao;
        this.repaymentDao = repaymentDao;
        this.transactionDao = transactionDao;
        this.vendorDao = vendorDao;
        this.scoreService = scoreService;
    }

    /** What the vendor could borrow right now. */
    public record Offer(int score, RiskBand band, double maxAmount, double interestRate, String note) { }

    public Offer offerFor(long vendorId) throws DataAccessException {
        int score = scoreService.scoreValue(vendorId);
        RiskBand band = RiskBand.fromScore(score);
        int minScore = AppConfig.getInstance().getInt("loan.min.score");
        double rate = interestFor(score);
        String note;
        double max = band.getMaxLoan();
        if (score < minScore) {
            max = 0;
            note = "Score below " + minScore + " — build history with regular transactions and vouches";
        } else if (loanDao.findByVendor(vendorId).stream().anyMatch(l -> l.getStatus() == LoanStatus.DEFAULTED)) {
            max = 0;
            note = "Has a defaulted loan — not eligible for new credit";
        } else if (hasOpenLoan(vendorId)) {
            max = 0;
            note = "An existing loan must be repaid first";
        } else {
            note = "Eligible up to " + Formats.inr(max) + " at " + String.format("%.1f%%", rate) + " p.a.";
        }
        return new Offer(score, band, max, rate, note);
    }

    /** Better score → cheaper credit. Clamped to 12%..30% p.a. */
    public double interestFor(int score) {
        double base = AppConfig.getInstance().getDouble("loan.interest.base");
        double rate = base + (650 - score) * 0.02;
        return Math.round(Math.max(12, Math.min(30, rate)) * 10) / 10.0;
    }

    private boolean hasOpenLoan(long vendorId) throws DataAccessException {
        return loanDao.findByVendor(vendorId).stream().anyMatch(l -> l.getStatus().isOpen());
    }

    /**
     * Issues a loan: inserts the loan and a disbursal transaction in ONE database transaction,
     * so either both rows exist or neither does.
     */
    public Loan issueLoan(long vendorId, double amount, int tenureWeeks, LocalDate issueDate)
            throws ValidationException, LoanEligibilityException, DataAccessException, EntityNotFoundException {

        Vendor vendor = vendorDao.findById(vendorId).orElseThrow(() -> new EntityNotFoundException("Vendor", vendorId));
        if (!vendor.isActive()) {
            throw new LoanEligibilityException(vendor.getName() + " is marked inactive", 0);
        }
        Validators.requirePositive("amount", amount);
        Validators.requireRange("tenure (weeks)", tenureWeeks, 4, 52);
        Validators.requireNotFuture("issue date", issueDate);

        Offer offer = offerFor(vendorId);
        if (offer.maxAmount() <= 0) {
            throw new LoanEligibilityException(offer.note(), 0);
        }
        if (amount > offer.maxAmount()) {
            throw new LoanEligibilityException("Requested " + Formats.inr(amount) + " exceeds the "
                    + offer.band().getLabel() + " band limit of " + Formats.inr(offer.maxAmount()), offer.maxAmount());
        }

        Loan loan = new Loan(0, vendorId, amount, offer.interestRate(), tenureWeeks, issueDate,
                issueDate.plusWeeks(tenureWeeks), LoanStatus.ACTIVE, offer.score());

        db.transaction(() -> {
            loanDao.save(loan);
            transactionDao.save(new Transaction(vendorId, amount, Transaction.Type.LOAN_DISBURSAL,
                    issueDate, "Loan #" + loan.getId() + " disbursed"));
            return loan;
        });
        LOG.info("Issued loan #" + loan.getId() + " of " + Formats.inr(amount) + " to " + vendor.getName());
        return loan;
    }

    /** Records an instalment, then closes the loan if it is fully paid (one transaction). */
    public Repayment recordRepayment(long loanId, double amount, LocalDate paidDate)
            throws ValidationException, DataAccessException, EntityNotFoundException {

        Loan loan = loanDao.findById(loanId).orElseThrow(() -> new EntityNotFoundException("Loan", loanId));
        Validators.requirePositive("amount", amount);
        Validators.requireNotFuture("payment date", paidDate);
        if (!loan.getStatus().isOpen()) {
            throw new ValidationException("loan", "Loan #" + loanId + " is " + loan.getStatus().getLabel().toLowerCase());
        }
        if (paidDate.isBefore(loan.getIssueDate())) {
            throw new ValidationException("payment date", "Payment cannot be before the loan was issued");
        }
        double repaid = repaymentDao.totalRepaid(loanId);
        double outstanding = loan.getTotalPayable() - repaid;
        if (amount > outstanding + 1) {
            throw new ValidationException("amount", "Only " + Formats.inr(outstanding) + " is outstanding");
        }

        int instalmentNo = repaymentDao.findByLoan(loanId).size() + 1;
        LocalDate due = loan.instalmentDueDate(Math.min(instalmentNo, loan.getTenureWeeks()));
        Repayment repayment = Repayment.of(loanId, amount, due, paidDate);

        db.transaction(() -> {
            repaymentDao.save(repayment);
            if (repaid + amount >= loan.getTotalPayable() - 0.5) {
                loanDao.updateStatus(loanId, LoanStatus.REPAID);
            }
            return repayment;
        });
        LOG.info("Repayment of " + Formats.inr(amount) + " on loan #" + loanId + (repayment.isOnTime() ? " (on time)" : " (late)"));
        return repayment;
    }

    public void markDefaulted(long loanId) throws DataAccessException {
        loanDao.updateStatus(loanId, LoanStatus.DEFAULTED);
        LOG.warning("Loan #" + loanId + " marked as DEFAULTED");
    }

    /** ACTIVE loans past their due date become OVERDUE. Returns how many changed. */
    public int refreshOverdue(LocalDate today) throws DataAccessException {
        int changed = 0;
        for (Loan l : loanDao.findByStatus(LoanStatus.ACTIVE)) {
            if (today.isAfter(l.getDueDate())) {
                loanDao.updateStatus(l.getId(), LoanStatus.OVERDUE);
                changed++;
            }
        }
        if (changed > 0) LOG.info(changed + " loan(s) moved to OVERDUE");
        return changed;
    }

    /** All loans with repaid amount and vendor name filled in, for tables. */
    public List<Loan> listLoans() throws DataAccessException {
        List<Loan> loans = loanDao.findAll();
        Map<Long, Double> totals = repaymentDao.totalsByLoan();
        Map<Long, String> names = new HashMap<>();
        for (Vendor v : vendorDao.findAll()) names.put(v.getId(), v.getName());
        for (Loan l : loans) {
            l.setAmountRepaid(totals.getOrDefault(l.getId(), 0.0));
            l.setVendorName(names.getOrDefault(l.getVendorId(), "#" + l.getVendorId()));
        }
        return loans;
    }

    public List<Loan> loansOf(long vendorId) throws DataAccessException {
        return listLoans().stream().filter(l -> l.getVendorId() == vendorId).toList();
    }

    public List<Repayment> repaymentsOf(long loanId) throws DataAccessException {
        return repaymentDao.findByLoan(loanId);
    }
}
