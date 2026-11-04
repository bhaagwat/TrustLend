package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Loan;
import com.trustlend.model.LoanStatus;

import java.util.List;

public interface LoanDao extends GenericDao<Loan> {

    List<Loan> findByVendor(long vendorId) throws DataAccessException;

    List<Loan> findByStatus(LoanStatus status) throws DataAccessException;

    void updateStatus(long loanId, LoanStatus status) throws DataAccessException;
}
