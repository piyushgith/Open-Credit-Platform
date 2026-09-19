package com.opencredit.platform.audit.model;

/**
 * The named business events from {@code my_docs/plan.md} §8's Week 10 list, plus
 * {@code APPLICATION_SUBMITTED} and {@code APPROVAL_DECISION_RECORDED} — not on that literal list,
 * but the same kind of already-owned state transition, and maker-checker is precisely where
 * auditability matters most. See the Week 10 plan's "Business audit" section for the exact
 * call site each one is emitted from.
 */
public enum AuditEventType {
    APPLICATION_SUBMITTED,
    UNDERWRITING_STARTED,
    UNDERWRITING_COMPLETED,
    DECISION_CREATED,
    APPROVAL_DECISION_RECORDED,
    OFFER_CREATED,
    OFFER_ACCEPTED,
    SANCTION_APPROVED,
    DISBURSEMENT_CREATED
}
