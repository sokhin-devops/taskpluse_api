package com.taskpulse.api.exception;

/**
 * Thrown when a task lookup does not resolve to a row owned by the caller.
 * Translated to a 404 ApiError by the global exception handler.
 *
 * <p>A task that exists but belongs to somebody else produces this same 404 rather than a
 * 403: answering "forbidden" would confirm that the id is real, which leaks the existence
 * of other people's tasks to anyone willing to enumerate ids.</p>
 */
public class TaskNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaskNotFoundException(Long id) {
        super("Task not found with id " + id);
    }
}
