package com.taskpulse.api.exception;

/**
 * Thrown when a task lookup does not resolve to an existing row.
 * Translated to a 404 ApiError by the global exception handler.
 */
public class TaskNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaskNotFoundException(Long id) {
        super("Task not found with id " + id);
    }
}
