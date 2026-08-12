package org.doscolas.exception;

/** Replaces the old {@code ResourceAlreadyExistsException} (username/email taken, duplicate assignment, ...). */
public final class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String message) {
        super(409, "DUPLICATE_RESOURCE", message);
    }
}
