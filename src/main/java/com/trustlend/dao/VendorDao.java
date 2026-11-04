package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Vendor;

import java.util.List;
import java.util.Optional;

public interface VendorDao extends GenericDao<Vendor> {

    Optional<Vendor> findByPhone(String phone) throws DataAccessException;

    /** Case-insensitive search on name, phone, market area and city. */
    List<Vendor> search(String text) throws DataAccessException;
}
