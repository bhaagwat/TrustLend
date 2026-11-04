package com.trustlend.exception;

/** Invalid user input. {@link #getField()} tells the UI which control to highlight. */
public class ValidationException extends TrustLendException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
