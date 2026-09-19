package com.opencredit.platform.disbursement.exception;

import java.math.BigDecimal;

public class DisbursementExceedsSanctionedAmountException extends RuntimeException {

    public DisbursementExceedsSanctionedAmountException(BigDecimal requestedAmount, BigDecimal disbursedTotal,
                                                          BigDecimal sanctionedAmount) {
        super("Tranche amount " + requestedAmount + " plus already-disbursed " + disbursedTotal
                + " would exceed the sanctioned amount " + sanctionedAmount);
    }
}
