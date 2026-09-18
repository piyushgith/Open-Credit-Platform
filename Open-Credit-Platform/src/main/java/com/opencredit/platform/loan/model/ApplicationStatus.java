package com.opencredit.platform.loan.model;

/**
 * Lifecycle status of a {@link LoanApplication}. This first cut only ever reaches
 * {@code PROCESSED} (the full origination lifecycle in my_docs/plan.md is a later stage).
 */
public enum ApplicationStatus {
    PROCESSED
}
