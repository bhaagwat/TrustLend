package com.trustlend.exception;

/** Thrown by DAOs. Wraps the low-level {@link java.sql.SQLException} (exception chaining). */
public class DataAccessException extends TrustLendException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
