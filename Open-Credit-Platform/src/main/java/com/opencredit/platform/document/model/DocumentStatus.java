package com.opencredit.platform.document.model;

/**
 * Verification status of a {@link LoanDocument}. Legal transitions are enforced by
 * {@link com.opencredit.platform.document.LoanDocumentService}, not by this enum itself.
 */
public enum DocumentStatus {
    UPLOADED,
    VERIFIED,
    REJECTED
}
