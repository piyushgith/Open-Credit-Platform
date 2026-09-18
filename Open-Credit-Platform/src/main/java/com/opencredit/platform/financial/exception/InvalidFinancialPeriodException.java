package com.opencredit.platform.financial.exception;

import java.time.LocalDate;

public class InvalidFinancialPeriodException extends RuntimeException {

    public InvalidFinancialPeriodException(LocalDate startDate, LocalDate endDate) {
        super("Financial period start date " + startDate + " must be before end date " + endDate);
    }
}
