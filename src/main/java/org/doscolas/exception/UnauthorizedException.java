package org.doscolas.exception;

/** Missing/invalid/expired bearer token — there's no Spring Security filter chain to produce this anymore. */
public final class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(401, "UNAUTHORIZED", message);
    }
}
