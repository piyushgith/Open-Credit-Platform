package com.opencredit.platform.kyc.model;

/**
 * Verification status of a {@link KycCase}. Legal transitions are enforced by
 * {@link com.opencredit.platform.kyc.KycService}, not by this enum itself.
 */
public enum KycStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
