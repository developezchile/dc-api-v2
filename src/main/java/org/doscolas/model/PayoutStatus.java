package org.doscolas.model;

public enum PayoutStatus {
    /** Payment succeeded but the sitter hasn't registered a bank account yet — held until they do. */
    PENDING_BANK_ACCOUNT,
    /** Ready to send, or waiting to be retried after a retryable failure. */
    PENDING,
    /** A transfer was submitted to Fintoc and is in flight. */
    PROCESSING,
    COMPLETED,
    FAILED
}
