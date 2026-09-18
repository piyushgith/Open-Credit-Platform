package com.opencredit.platform.loan.model;

/**
 * Lifecycle status of a {@link LoanApplication}. Legal transitions are enforced by
 * {@link com.opencredit.platform.loan.support.ApplicationLifecycle}, not by this enum itself.
 */
public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    UNDERWRITING,
    OFFERED,
    DECLINED,
    SANCTIONED,
    DISBURSED
}
