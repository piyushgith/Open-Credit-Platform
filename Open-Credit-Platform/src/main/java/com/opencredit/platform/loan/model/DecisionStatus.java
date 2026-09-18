package com.opencredit.platform.loan.model;

/**
 * Outcome of a strategy's decision. Shared by the persisted {@link LoanApplication}
 * and every product's response DTO.
 */
public enum DecisionStatus {
    APPROVED,
    REFERRED,
    DECLINED
}
