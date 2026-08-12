package org.doscolas.exception;

/** Unretryable-by-design failure talking to Fintoc's Checkout Sessions API (payment collection). */
public final class FintocApiException extends RuntimeException {
    public FintocApiException(String message) {
        super(message);
    }

    public FintocApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
