package com.opencredit.platform.underwriting.model;

/**
 * Lifecycle status of an {@link UnderwritingAttempt}. Legal transitions are enforced by
 * {@link com.opencredit.platform.underwriting.support.UnderwritingAttemptLifecycle}, not by
 * this enum itself. Names match {@link com.opencredit.platform.loan.model.DecisionStatus} so a
 * strategy decision maps onto an outcome by {@code name()} alone.
 */
public enum UnderwritingAttemptStatus {
    IN_PROGRESS,
    APPROVED,
    DECLINED,
    REFERRED
}
