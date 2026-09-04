package com.taskpulse.api.exception;

/**
 * Thrown when a registration uses an email that already has an account.
 * Translated to a 409 ApiError by the global exception handler.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyUsedException(String email) {
        super("An account already exists for " + email);
    }
}
