package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Identifiable;

import java.util.List;
import java.util.Optional;

/**
 * DAO design pattern (Unit 4): the rest of the app talks to this interface and never
 * sees SQL. Generic in the entity type, bounded by {@link Identifiable}.
 *
 * @param <T> entity type
 */
public interface GenericDao<T extends Identifiable> {

    /** INSERT when the entity is new, UPDATE otherwise. Returns the same object with its id set. */
    T save(T entity) throws DataAccessException;

    Optional<T> findById(long id) throws DataAccessException;

    List<T> findAll() throws DataAccessException;

    boolean delete(long id) throws DataAccessException;

    long count() throws DataAccessException;
}
