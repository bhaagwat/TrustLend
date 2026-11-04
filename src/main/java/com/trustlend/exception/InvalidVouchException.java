package com.trustlend.exception;

public class InvalidVouchException extends TrustLendException {

    private static final long serialVersionUID = 1L;

    public InvalidVouchException(String message) {
        super(message);
    }
}
