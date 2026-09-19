package com.opencredit.platform.financial.support;

import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import com.opencredit.platform.financial.model.FinancialRatioCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Computes {@link FinancialRatioCode} values from {@link DerivedFinancialFactCode}s where one
 * exists for the figure, falling back to a raw {@link FinancialLineItemCode} only for atomic
 * figures with no derived composite (current assets/liabilities, equity, revenue, interest
 * expense, short-term debt, inventory).
 *
 * <p>A ratio whose inputs are missing, or whose denominator is zero, is simply left out of the
 * result — never defaulted to zero, never thrown. Every result is rounded to
 * {@value #RATIO_SCALE} decimal places, a deliberately finer scale than the money scale used for
 * line items/derived facts since ratios are dimensionless, not currency.
 *
 * <p>{@code DSCR} is computed as {@code EBITDA / (INTEREST_EXPENSE + SHORT_TERM_DEBT)} — the
 * schema models no debt-service schedule or "current portion of long-term debt", so
 * {@code SHORT_TERM_DEBT} stands in as the current-period principal obligation. This is a
 * documented simplification, not textbook DSCR.
 */
@Component
public class RatioCalculator {

    private static final int RATIO_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Map<FinancialRatioCode, BigDecimal> calculate(Map<FinancialLineItemCode, BigDecimal> rawValues,
                                                           Map<DerivedFinancialFactCode, BigDecimal> facts) {
        Map<FinancialRatioCode, BigDecimal> ratios = new EnumMap<>(FinancialRatioCode.class);

        put(ratios, FinancialRatioCode.CURRENT_RATIO,
                divide(raw(rawValues, FinancialLineItemCode.CURRENT_ASSETS),
                        raw(rawValues, FinancialLineItemCode.CURRENT_LIABILITIES)));

        put(ratios, FinancialRatioCode.QUICK_RATIO,
                divide(subtract(raw(rawValues, FinancialLineItemCode.CURRENT_ASSETS),
                                raw(rawValues, FinancialLineItemCode.INVENTORY)),
                        raw(rawValues, FinancialLineItemCode.CURRENT_LIABILITIES)));

        put(ratios, FinancialRatioCode.DEBT_TO_EQUITY,
                divide(fact(facts, DerivedFinancialFactCode.TOTAL_DEBT),
                        raw(rawValues, FinancialLineItemCode.EQUITY)));

        put(ratios, FinancialRatioCode.DEBT_TO_EBITDA,
                divide(fact(facts, DerivedFinancialFactCode.TOTAL_DEBT),
                        fact(facts, DerivedFinancialFactCode.EBITDA)));

        put(ratios, FinancialRatioCode.INTEREST_COVERAGE,
                divide(fact(facts, DerivedFinancialFactCode.EBITDA),
                        raw(rawValues, FinancialLineItemCode.INTEREST_EXPENSE)));

        put(ratios, FinancialRatioCode.GROSS_MARGIN,
                divide(fact(facts, DerivedFinancialFactCode.GROSS_PROFIT),
                        raw(rawValues, FinancialLineItemCode.REVENUE)));

        put(ratios, FinancialRatioCode.EBITDA_MARGIN,
                divide(fact(facts, DerivedFinancialFactCode.EBITDA),
                        raw(rawValues, FinancialLineItemCode.REVENUE)));

        put(ratios, FinancialRatioCode.NET_MARGIN,
                divide(fact(facts, DerivedFinancialFactCode.NET_PROFIT),
                        raw(rawValues, FinancialLineItemCode.REVENUE)));

        put(ratios, FinancialRatioCode.ROA,
                divide(fact(facts, DerivedFinancialFactCode.NET_PROFIT),
                        fact(facts, DerivedFinancialFactCode.TOTAL_ASSETS)));

        put(ratios, FinancialRatioCode.ROE,
                divide(fact(facts, DerivedFinancialFactCode.NET_PROFIT),
                        raw(rawValues, FinancialLineItemCode.EQUITY)));

        put(ratios, FinancialRatioCode.DSCR,
                divide(fact(facts, DerivedFinancialFactCode.EBITDA),
                        add(raw(rawValues, FinancialLineItemCode.INTEREST_EXPENSE),
                                raw(rawValues, FinancialLineItemCode.SHORT_TERM_DEBT))));

        return ratios;
    }

    private static Optional<BigDecimal> raw(Map<FinancialLineItemCode, BigDecimal> rawValues,
                                             FinancialLineItemCode code) {
        return Optional.ofNullable(rawValues.get(code));
    }

    private static Optional<BigDecimal> fact(Map<DerivedFinancialFactCode, BigDecimal> facts,
                                              DerivedFinancialFactCode code) {
        return Optional.ofNullable(facts.get(code));
    }

    private static void put(Map<FinancialRatioCode, BigDecimal> ratios, FinancialRatioCode code,
                             Optional<BigDecimal> value) {
        value.ifPresent(v -> ratios.put(code, v));
    }

    private static Optional<BigDecimal> add(Optional<BigDecimal> a, Optional<BigDecimal> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(a.get().add(b.get()));
    }

    private static Optional<BigDecimal> subtract(Optional<BigDecimal> minuend, Optional<BigDecimal> subtrahend) {
        if (minuend.isEmpty() || subtrahend.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(minuend.get().subtract(subtrahend.get()));
    }

    private static Optional<BigDecimal> divide(Optional<BigDecimal> numerator, Optional<BigDecimal> denominator) {
        if (numerator.isEmpty() || denominator.isEmpty() || denominator.get().compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }
        return Optional.of(numerator.get().divide(denominator.get(), RATIO_SCALE, ROUNDING));
    }
}
