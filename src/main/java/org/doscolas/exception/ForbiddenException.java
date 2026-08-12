package org.doscolas.exception;

/** Replaces the old {@code ForbiddenOperationException} — authenticated, but not allowed to do this. */
public final class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }
}
