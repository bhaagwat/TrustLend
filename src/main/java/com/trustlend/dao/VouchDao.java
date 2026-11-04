package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.Vouch;

import java.util.List;
import java.util.Optional;

public interface VouchDao extends GenericDao<Vouch> {

    List<Vouch> findGivenBy(long voucherId) throws DataAccessException;

    List<Vouch> findReceivedBy(long voucheeId) throws DataAccessException;

    Optional<Vouch> find(long voucherId, long voucheeId) throws DataAccessException;
}
