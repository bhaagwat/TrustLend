package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.User;

import java.util.List;
import java.util.Optional;

/** Login accounts: borrowers, lenders and TrustLend HQ. */
public interface UserDao {

    Optional<User> findByUsername(String username) throws DataAccessException;

    Optional<User> findById(long id) throws DataAccessException;

    /** The borrower account that belongs to a vendor profile, if the vendor has one. */
    Optional<User> findByVendor(long vendorId) throws DataAccessException;

    List<User> findAll() throws DataAccessException;

    List<User> findByRole(User.Role role) throws DataAccessException;

    User save(User user) throws DataAccessException;

    long count() throws DataAccessException;
}
