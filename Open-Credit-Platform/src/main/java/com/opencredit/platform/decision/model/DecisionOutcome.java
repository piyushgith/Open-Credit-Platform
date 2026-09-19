package com.opencredit.platform.decision.model;

/**
 * The outcome of a {@link CreditDecision}. Distinct from {@code loan.model.DecisionStatus}
 * (which drives the separate, FOIR-based decision {@code LoanProcessingStrategy} makes at
 * application submission time): this is the later-stage, policy-driven decision computed from a
 * {@code Score} once financial analysis has run, and it never writes back to
 * {@code LoanApplication}.
 */
public enum DecisionOutcome {
    APPROVE,
    REFER,
    DECLINE
}
