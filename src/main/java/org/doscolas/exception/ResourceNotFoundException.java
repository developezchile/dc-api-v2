package org.doscolas.exception;

public final class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }
}
