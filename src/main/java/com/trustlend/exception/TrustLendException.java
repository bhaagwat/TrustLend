package com.trustlend.exception;

/**
 * Root of the application's checked-exception hierarchy:
 *
 * <pre>
 * Exception
 *  └── TrustLendException            (checked, application-level)
 *       ├── DataAccessException      (wraps java.sql.SQLException from the DAO layer)
 *       ├── ValidationException      (bad user input, carries the offending field)
 *       ├── EntityNotFoundException  (lookup by id failed)
 *       ├── InvalidVouchException    (self-vouch, duplicate, cycle abuse)
 *       └── LoanEligibilityException (score too low / open loan exists / over limit)
 * </pre>
 */
public class TrustLendException extends Exception {

    private static final long serialVersionUID = 1L;

    public TrustLendException(String message) {
        super(message);
    }

    public TrustLendException(String message, Throwable cause) {
        super(message, cause);
    }
}
