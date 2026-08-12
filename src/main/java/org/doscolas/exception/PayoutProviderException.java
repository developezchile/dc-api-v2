package org.doscolas.exception;

/**
 * Signals a failure talking to Fintoc's Transfers API. Not an {@link ApiException} — it never
 * reaches an HTTP client directly, only {@link org.doscolas.service.PayoutService}, which decides
 * whether to retry based on {@link #isRetryable()} (a network blip or a 5xx is worth retrying; a
 * validation error like an invalid RUT or account number is not).
 */
public final class PayoutProviderException extends RuntimeException {

    private final boolean retryable;

    public PayoutProviderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public PayoutProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
