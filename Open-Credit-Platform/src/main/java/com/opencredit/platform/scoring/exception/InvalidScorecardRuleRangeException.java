package com.opencredit.platform.scoring.exception;

import java.math.BigDecimal;

public class InvalidScorecardRuleRangeException extends RuntimeException {

    public InvalidScorecardRuleRangeException(BigDecimal minValue, BigDecimal maxValue) {
        super("A rule's minValue must be strictly less than its maxValue when both are set, got minValue="
                + minValue + ", maxValue=" + maxValue);
    }
}
