package com.taskpulse.api.exception;

import java.util.Collection;

/**
 * Thrown when a tag lookup does not resolve to a row owned by the caller.
 * Translated to a 404 ApiError by the global exception handler.
 */
public class TagNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TagNotFoundException(Long id) {
        super("Tag not found with id " + id);
    }

    public TagNotFoundException(Collection<Long> ids) {
        super("Tags not found with ids " + ids);
    }
}
