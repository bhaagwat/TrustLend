package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Transaction;

import java.time.LocalDate;
import java.util.List;

public interface TransactionDao extends GenericDao<Transaction> {

    List<Transaction> findByVendor(long vendorId) throws DataAccessException;

    List<Transaction> findByVendorBetween(long vendorId, LocalDate from, LocalDate to) throws DataAccessException;

    List<Transaction> findRecent(int limit) throws DataAccessException;

    /** Inserts many rows efficiently with JDBC batching. Returns number inserted. */
    int insertBatch(List<Transaction> transactions) throws DataAccessException;
}
