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

    List<Repayment> findByStatus(Repayment.Verification status) throws DataAccessException;

    /** One borrower's repayments in a given state, e.g. those still waiting for their confirmation. */
    List<Repayment> findByStatusForVendor(long vendorId, Repayment.Verification status) throws DataAccessException;

    /** Everything a lender has reported. */
    List<Repayment> findReportedBy(long userId) throws DataAccessException;

    void updateStatus(long repaymentId, Repayment.Verification status, String note) throws DataAccessException;
}
