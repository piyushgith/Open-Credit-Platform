package com.opencredit.platform.authority.model;

/**
 * Who must sign off, ranked by seniority via ordinal: {@code AUTO < CREDIT_OFFICER <
 * SENIOR_CREDIT_MANAGER}. Mirrors {@code decision.model.RequiredAuthority}'s three values, but is
 * resolved from the configurable {@link AuthorityMatrixEntry} table rather than a fixed
 * outcome/risk-grade mapping.
 */
public enum ApprovalLevel {
    AUTO,
    CREDIT_OFFICER,
    SENIOR_CREDIT_MANAGER;

    public boolean atLeast(ApprovalLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
