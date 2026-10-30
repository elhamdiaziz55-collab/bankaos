package com.bankaos.transactionservice.enums;

/**
 * Transaction Lifecycle Flow:
 * PENDING -> PROCESSING -> COMPLETED (clean transaction)
 *                       -> PENDING_VERIFICATION (suspicious action detected)
 *                              -> COMPLETED (verified)
 *                              -> FLAGGED (SAGA REFUND)
 *                       -> FAILED
 *                       -> FLAGGED
 *
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    PENDING_VERIFICATION,
    FLAGGED,
    FAILED
}
