package com.trustlend.dao;

import com.trustlend.exception.DataAccessException;
import com.trustlend.model.ScoreBreakdown;

import java.util.Collection;
import java.util.List;

/** Stores a snapshot of every computed score so the UI can draw score-over-time charts. */
public interface ScoreHistoryDao {

    /** Replaces today's snapshot (if any) for each vendor and inserts the new ones, in one transaction. */
    void saveAll(Collection<ScoreBreakdown> scores) throws DataAccessException;

    List<ScoreBreakdown> findByVendor(long vendorId) throws DataAccessException;
}
