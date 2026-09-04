package com.taskpulse.api.exception;

/**
 * Thrown when a login presents an unknown email or a wrong password.
 * Translated to a 401 ApiError by the global exception handler.
 *
 * <p>The message never says which of the two was wrong: distinguishing them would let a
 * caller probe for registered email addresses.</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Incorrect email or password");
    }
}
