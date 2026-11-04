package com.trustlend.service;

import com.trustlend.dao.TransactionDao;
import com.trustlend.dao.VendorDao;
import com.trustlend.exception.DataAccessException;
import com.trustlend.exception.EntityNotFoundException;
import com.trustlend.exception.ValidationException;
import com.trustlend.model.Transaction;
import com.trustlend.util.Validators;

import java.time.LocalDate;
import java.util.List;

public class TransactionService {

    private final TransactionDao transactionDao;
    private final VendorDao vendorDao;

    public TransactionService(TransactionDao transactionDao, VendorDao vendorDao) {
        this.transactionDao = transactionDao;
        this.vendorDao = vendorDao;
    }

    public Transaction record(long vendorId, double amount, Transaction.Type type, LocalDate date, String note)
            throws ValidationException, DataAccessException, EntityNotFoundException {
        vendorDao.findById(vendorId).orElseThrow(() -> new EntityNotFoundException("Vendor", vendorId));
        Validators.requirePositive("amount", amount);
        if (amount > 10_00_000) {
            throw new ValidationException("amount", "Amount looks too large for a single micro-business entry");
        }
        if (type == null) throw new ValidationException("type", "Choose a transaction type");
        if (type == Transaction.Type.LOAN_DISBURSAL) {
            throw new ValidationException("type", "Loan disbursals are recorded automatically");
        }
        Validators.requireNotFuture("date", date);
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > 200) {
            throw new ValidationException("note", "Note must be under 200 characters");
        }
        return transactionDao.save(new Transaction(vendorId, amount, type, date, cleanNote));
    }

    public boolean delete(long id) throws DataAccessException {
        return transactionDao.delete(id);
    }

    public List<Transaction> forVendor(long vendorId) throws DataAccessException {
        return transactionDao.findByVendor(vendorId);
    }

    public List<Transaction> forVendorBetween(long vendorId, LocalDate from, LocalDate to) throws DataAccessException {
        return transactionDao.findByVendorBetween(vendorId, from, to);
    }

    public List<Transaction> all() throws DataAccessException {
        return transactionDao.findAll();
    }

    public List<Transaction> recent(int limit) throws DataAccessException {
        return transactionDao.findRecent(limit);
    }

    public int importBatch(List<Transaction> list) throws DataAccessException {
        return transactionDao.insertBatch(list);
    }
}
