package com.opencredit.platform.security.model;

/**
 * The three roles that gate an endpoint: {@code MAKER}/{@code CHECKER} for the two sides of the
 * Week 8 maker-checker workflow, {@code ADMIN} for the credit-policy/scorecard/authority-matrix
 * configuration endpoints (and as a universal override for demo purposes). {@code my_docs/plan.md}
 * §8 also names "Credit Officer" as a role, but that concept already exists one level down as
 * {@link com.opencredit.platform.authority.model.ApprovalLevel#CREDIT_OFFICER} — the seniority a
 * {@code CHECKER} is authorized to approve at ({@link AppUser#getApprovalLevel()}). Modeling it
 * again as a fourth top-level role would duplicate that distinction rather than express a
 * different one.
 */
public enum AppRole {
    MAKER,
    CHECKER,
    ADMIN
}
