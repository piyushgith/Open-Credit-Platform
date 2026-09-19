package com.opencredit.platform.disbursement.model;

/**
 * {@code IN_PROGRESS -> COMPLETED}, the latter reached exactly when the running total of every
 * {@link DisbursementTranche} equals the sanctioned amount.
 */
public enum DisbursementStatus {
    IN_PROGRESS,
    COMPLETED
}
