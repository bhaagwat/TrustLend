package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.User;

import java.util.Optional;

public interface UserDao {

    Optional<User> findByUsername(String username) throws DataAccessException;

    User save(User user) throws DataAccessException;

    long count() throws DataAccessException;
}
