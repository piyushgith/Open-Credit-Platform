package com.opencredit.platform.financial.support;

import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import com.opencredit.platform.financial.model.FinancialRatioCode;
import com.opencredit.platform.financial.model.RiskIndicatorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates {@link RiskIndicatorCode} checks from ratios/facts already computed for the current
 * run, plus (for the two comparative indicators) the previous financial period's figures.
 *
 * <p>A row is produced only for an indicator whose inputs were all present — never defaulted,
 * never thrown — same missing-propagates rule as {@link RatioCalculator} and
 * {@link DerivedFactCalculator}. {@code HIGH_LEVERAGE}/{@code LOW_LIQUIDITY}/
 * {@code WEAK_INTEREST_COVERAGE} reuse an already-computed ratio rather than re-deriving raw
 * values; thresholds are documented per-field below.
 */
@Component
public class RiskIndicatorEvaluator {

    private static final BigDecimal HIGH_LEVERAGE_THRESHOLD = new BigDecimal("2.00");
    private static final BigDecimal LOW_LIQUIDITY_THRESHOLD = BigDecimal.ONE;
    private static final BigDecimal WEAK_INTEREST_COVERAGE_THRESHOLD = new BigDecimal("1.50");

    /** Previous-period figures for the two comparative indicators; empty when not available. */
    public record PreviousPeriodFacts(Optional<BigDecimal> revenue, Optional<BigDecimal> ebitda) {

        public static PreviousPeriodFacts none() {
            return new PreviousPeriodFacts(Optional.empty(), Optional.empty());
        }
    }

    public Map<RiskIndicatorCode, Boolean> evaluate(Map<FinancialRatioCode, BigDecimal> ratios,
                                                      Map<DerivedFinancialFactCode, BigDecimal> facts,
                                                      Map<FinancialLineItemCode, BigDecimal> rawValues,
                                                      PreviousPeriodFacts previousPeriod) {
        Map<RiskIndicatorCode, Boolean> indicators = new EnumMap<>(RiskIndicatorCode.class);

        compare(rawValues.get(FinancialLineItemCode.REVENUE), previousPeriod.revenue())
                .ifPresent(triggered -> indicators.put(RiskIndicatorCode.DECLINING_REVENUE, triggered));

        compare(facts.get(DerivedFinancialFactCode.EBITDA), previousPeriod.ebitda())
                .ifPresent(triggered -> indicators.put(RiskIndicatorCode.DECLINING_EBITDA, triggered));

        // NET_PROFIT + DEPRECIATION_AMORTIZATION as a cash-flow proxy (adding back the largest
        // non-cash expense) — the schema has no cash-flow-statement line items to compute a real
        // operating cash flow from, so this is a deliberate simplification, not the full formula.
        Optional<BigDecimal> netProfit = Optional.ofNullable(facts.get(DerivedFinancialFactCode.NET_PROFIT));
        Optional<BigDecimal> depreciation =
                Optional.ofNullable(rawValues.get(FinancialLineItemCode.DEPRECIATION_AMORTIZATION));
        if (netProfit.isPresent() && depreciation.isPresent()) {
            indicators.put(RiskIndicatorCode.NEGATIVE_CASH_FLOW,
                    netProfit.get().add(depreciation.get()).compareTo(BigDecimal.ZERO) < 0);
        }

        threshold(ratios.get(FinancialRatioCode.DEBT_TO_EQUITY), HIGH_LEVERAGE_THRESHOLD, true)
                .ifPresent(triggered -> indicators.put(RiskIndicatorCode.HIGH_LEVERAGE, triggered));

        threshold(ratios.get(FinancialRatioCode.CURRENT_RATIO), LOW_LIQUIDITY_THRESHOLD, false)
                .ifPresent(triggered -> indicators.put(RiskIndicatorCode.LOW_LIQUIDITY, triggered));

        threshold(ratios.get(FinancialRatioCode.INTEREST_COVERAGE), WEAK_INTEREST_COVERAGE_THRESHOLD, false)
                .ifPresent(triggered -> indicators.put(RiskIndicatorCode.WEAK_INTEREST_COVERAGE, triggered));

        return indicators;
    }

    /** {@code current < previous}; empty (not evaluable) unless both values are present. */
    private static Optional<Boolean> compare(BigDecimal current, Optional<BigDecimal> previous) {
        if (current == null || previous.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(current.compareTo(previous.get()) < 0);
    }

    /** {@code value > threshold} when {@code above}, else {@code value < threshold}. */
    private static Optional<Boolean> threshold(BigDecimal value, BigDecimal threshold, boolean above) {
        if (value == null) {
            return Optional.empty();
        }
        int comparison = value.compareTo(threshold);
        return Optional.of(above ? comparison > 0 : comparison < 0);
    }
}
