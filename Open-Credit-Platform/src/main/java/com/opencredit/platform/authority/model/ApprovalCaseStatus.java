package com.opencredit.platform.authority.model;

/**
 * {@code PENDING_MAKER -> PENDING_CHECKER -> (APPROVED | REJECTED)}. The last two are terminal —
 * enforced by {@link com.opencredit.platform.authority.support.ApprovalLifecycle}.
 */
public enum ApprovalCaseStatus {
    PENDING_MAKER,
    PENDING_CHECKER,
    APPROVED,
    REJECTED
}
