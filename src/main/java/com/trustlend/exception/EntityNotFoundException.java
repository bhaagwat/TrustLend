package com.trustlend.exception;

public class EntityNotFoundException extends TrustLendException {

    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(String entity, long id) {
        super(entity + " with id " + id + " was not found");
    }
}
