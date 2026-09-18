package com.opencredit.platform.kyc.exception;

import com.opencredit.platform.kyc.model.KycStatus;

public class IllegalKycTransitionException extends RuntimeException {

    public IllegalKycTransitionException(KycStatus from, KycStatus to) {
        super("Cannot transition KYC case from " + from + " to " + to);
    }
}
