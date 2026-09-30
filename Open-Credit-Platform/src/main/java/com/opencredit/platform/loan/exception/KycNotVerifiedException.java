package com.opencredit.platform.loan.exception;

import com.opencredit.platform.kyc.model.KycStatus;

public class KycNotVerifiedException extends RuntimeException {

    public KycNotVerifiedException(String referenceNumber, KycStatus kycStatus) {
        super("Loan application " + referenceNumber + " cannot be sanctioned: KYC is "
                + (kycStatus == null ? "not initiated" : kycStatus) + ", must be VERIFIED");
    }
}
