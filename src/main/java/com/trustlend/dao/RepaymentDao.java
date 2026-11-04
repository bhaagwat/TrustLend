package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Repayment;

import java.util.List;
import java.util.Map;

public interface RepaymentDao extends GenericDao<Repayment> {

    List<Repayment> findByLoan(long loanId) throws DataAccessException;

    /** All repayments for every loan of a vendor (JOIN loans). */
    List<Repayment> findByVendor(long vendorId) throws DataAccessException;

    double totalRepaid(long loanId) throws DataAccessException;

    /** loanId → total repaid, for every loan in one GROUP BY query. */
    Map<Long, Double> totalsByLoan() throws DataAccessException;
}
