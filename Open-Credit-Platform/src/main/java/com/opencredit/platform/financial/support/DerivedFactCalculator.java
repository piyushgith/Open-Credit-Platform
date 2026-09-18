package com.opencredit.platform.financial.support;

import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Computes {@link DerivedFinancialFactCode} values from raw {@link FinancialLineItemCode}
 * values, in a fixed dependency order: {@code GROSS_PROFIT} before {@code OPERATING_PROFIT},
 * which in turn must be computed before {@code EBITDA}/{@code NET_PROFIT} read it back out of
 * the accumulating {@code derived} map.
 *
 * <p>A fact whose required inputs are (transitively) missing is simply left out of the result —
 * never defaulted to zero, never thrown. Every result is rounded to money scale after each step,
 * mirroring {@link com.opencredit.platform.loan.support.EmiCalculator}.
 */
@Component
public class DerivedFactCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Map<DerivedFinancialFactCode, BigDecimal> calculate(Map<FinancialLineItemCode, BigDecimal> rawValues) {
        Map<DerivedFinancialFactCode, BigDecimal> derived = new EnumMap<>(DerivedFinancialFactCode.class);

        put(derived, DerivedFinancialFactCode.TOTAL_ASSETS,
                add(raw(rawValues, FinancialLineItemCode.CURRENT_ASSETS),
                        raw(rawValues, FinancialLineItemCode.NON_CURRENT_ASSETS)));

        put(derived, DerivedFinancialFactCode.TOTAL_LIABILITIES,
                add(raw(rawValues, FinancialLineItemCode.CURRENT_LIABILITIES),
                        raw(rawValues, FinancialLineItemCode.NON_CURRENT_LIABILITIES)));

        put(derived, DerivedFinancialFactCode.WORKING_CAPITAL,
                subtract(raw(rawValues, FinancialLineItemCode.CURRENT_ASSETS),
                        raw(rawValues, FinancialLineItemCode.CURRENT_LIABILITIES)));

        put(derived, DerivedFinancialFactCode.TOTAL_DEBT,
                add(raw(rawValues, FinancialLineItemCode.LONG_TERM_DEBT),
                        raw(rawValues, FinancialLineItemCode.SHORT_TERM_DEBT)));

        put(derived, DerivedFinancialFactCode.GROSS_PROFIT,
                subtract(raw(rawValues, FinancialLineItemCode.REVENUE),
                        raw(rawValues, FinancialLineItemCode.COST_OF_GOODS_SOLD)));

        put(derived, DerivedFinancialFactCode.OPERATING_PROFIT,
                subtract(fact(derived, DerivedFinancialFactCode.GROSS_PROFIT),
                        raw(rawValues, FinancialLineItemCode.OPERATING_EXPENSES)));

        put(derived, DerivedFinancialFactCode.EBITDA,
                add(fact(derived, DerivedFinancialFactCode.OPERATING_PROFIT),
                        raw(rawValues, FinancialLineItemCode.DEPRECIATION_AMORTIZATION)));

        put(derived, DerivedFinancialFactCode.NET_PROFIT,
                subtract(fact(derived, DerivedFinancialFactCode.OPERATING_PROFIT),
                        raw(rawValues, FinancialLineItemCode.INTEREST_EXPENSE),
                        raw(rawValues, FinancialLineItemCode.TAX_EXPENSE)));

        return derived;
    }

    private static Optional<BigDecimal> raw(Map<FinancialLineItemCode, BigDecimal> rawValues,
                                             FinancialLineItemCode code) {
        return Optional.ofNullable(rawValues.get(code));
    }

    private static Optional<BigDecimal> fact(Map<DerivedFinancialFactCode, BigDecimal> derived,
                                              DerivedFinancialFactCode code) {
        return Optional.ofNullable(derived.get(code));
    }

    private static void put(Map<DerivedFinancialFactCode, BigDecimal> derived, DerivedFinancialFactCode code,
                             Optional<BigDecimal> value) {
        value.ifPresent(v -> derived.put(code, v));
    }

    private static Optional<BigDecimal> add(Optional<BigDecimal> a, Optional<BigDecimal> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(round(a.get().add(b.get())));
    }

    @SafeVarargs
    private static Optional<BigDecimal> subtract(Optional<BigDecimal> minuend, Optional<BigDecimal>... subtrahends) {
        if (minuend.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal result = minuend.get();
        for (Optional<BigDecimal> subtrahend : subtrahends) {
            if (subtrahend.isEmpty()) {
                return Optional.empty();
            }
            result = result.subtract(subtrahend.get());
        }
        return Optional.of(round(result));
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }
}
