package com.opencredit.platform.decision.model;

import java.math.BigDecimal;

/**
 * The comparison a {@link CreditRule} applies between a resolved input value and its configured
 * threshold. Deliberately just six pure comparisons — no expression language, no reflection.
 */
public enum ComparisonOperator {
    GTE {
        @Override
        public boolean evaluate(BigDecimal actual, BigDecimal threshold) {
            return actual.compareTo(threshold) >= 0;
        }
    },
    LTE {
        @Override
        public boolean evaluate(BigDecimal actual, BigDecimal threshold) {
            return actual.compareTo(threshold) <= 0;
        }
    },
    GT {
        @Override
        public boolean evaluate(BigDecimal actual, BigDecimal threshold) {
            return actual.compareTo(threshold) > 0;
        }
    },
    LT {
        @Override
        public boolean evaluate(BigDecimal actual, BigDecimal threshold) {
            return actual.compareTo(threshold) < 0;
        }
    },
    EQ {
        @Override
        public boolean evaluate(BigDecimal actual, BigDecimal threshold) {
            return actual.compareTo(threshold) == 0;
        }
    },
    NEQ {
        @Override
        public boolean evaluate(BigDecimal actual, BigDecimal threshold) {
            return actual.compareTo(threshold) != 0;
        }
    };

    public abstract boolean evaluate(BigDecimal actual, BigDecimal threshold);
}
