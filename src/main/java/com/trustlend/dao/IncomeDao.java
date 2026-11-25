package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.IncomeDeclaration;

import java.util.List;

/** Income declarations (salary slips, business income) and their verification. */
public interface IncomeDao extends GenericDao<IncomeDeclaration> {

    List<IncomeDeclaration> findByVendor(long vendorId) throws DataAccessException;

    List<IncomeDeclaration> findByStatus(IncomeDeclaration.Status status) throws DataAccessException;
}
