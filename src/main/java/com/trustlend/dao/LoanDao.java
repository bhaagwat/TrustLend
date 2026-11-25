package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;

import java.util.List;

public interface LoanDao extends GenericDao<Loan> {

    List<Loan> findByVendor(long vendorId) throws DataAccessException;

    List<Loan> findByStatus(LoanStatus status) throws DataAccessException;

    void updateStatus(long loanId, LoanStatus status) throws DataAccessException;

    /** Loans given (or reported) by one lender. */
    List<Loan> findByLender(long lenderId) throws DataAccessException;

    void assignLender(long loanId, long lenderId) throws DataAccessException;
}
