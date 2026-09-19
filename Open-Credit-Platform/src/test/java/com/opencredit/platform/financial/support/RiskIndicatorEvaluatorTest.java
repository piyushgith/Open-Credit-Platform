package com.opencredit.platform.financial.support;

import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import com.opencredit.platform.financial.model.FinancialRatioCode;
import com.opencredit.platform.financial.model.RiskIndicatorCode;
import com.opencredit.platform.financial.support.RiskIndicatorEvaluator.PreviousPeriodFacts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RiskIndicatorEvaluatorTest {

    private final RiskIndicatorEvaluator evaluator = new RiskIndicatorEvaluator();

    private static Map<FinancialRatioCode, BigDecimal> ratiosWith(FinancialRatioCode code, String value) {
        Map<FinancialRatioCode, BigDecimal> ratios = new EnumMap<>(FinancialRatioCode.class);
        ratios.put(code, new BigDecimal(value));
        return ratios;
    }

    private static Map<DerivedFinancialFactCode, BigDecimal> factsOf(DerivedFinancialFactCode code, String value) {
        Map<DerivedFinancialFactCode, BigDecimal> facts = new EnumMap<>(DerivedFinancialFactCode.class);
        facts.put(code, new BigDecimal(value));
        return facts;
    }

    private static Map<FinancialLineItemCode, BigDecimal> rawOf(FinancialLineItemCode code, String value) {
        Map<FinancialLineItemCode, BigDecimal> raw = new EnumMap<>(FinancialLineItemCode.class);
        raw.put(code, new BigDecimal(value));
        return raw;
    }

    @Test
    void highLeverageTriggersOnlyStrictlyAboveThreshold() {
        Map<RiskIndicatorCode, Boolean> above = evaluator.evaluate(
                ratiosWith(FinancialRatioCode.DEBT_TO_EQUITY, "2.01"), Map.of(), Map.of(), PreviousPeriodFacts.none());
        Map<RiskIndicatorCode, Boolean> atThreshold = evaluator.evaluate(
                ratiosWith(FinancialRatioCode.DEBT_TO_EQUITY, "2.00"), Map.of(), Map.of(), PreviousPeriodFacts.none());

        assertThat(above.get(RiskIndicatorCode.HIGH_LEVERAGE)).isTrue();
        assertThat(atThreshold.get(RiskIndicatorCode.HIGH_LEVERAGE)).isFalse();
    }

    @Test
    void lowLiquidityTriggersOnlyStrictlyBelowThreshold() {
        Map<RiskIndicatorCode, Boolean> below = evaluator.evaluate(
                ratiosWith(FinancialRatioCode.CURRENT_RATIO, "0.99"), Map.of(), Map.of(), PreviousPeriodFacts.none());
        Map<RiskIndicatorCode, Boolean> atThreshold = evaluator.evaluate(
                ratiosWith(FinancialRatioCode.CURRENT_RATIO, "1.00"), Map.of(), Map.of(), PreviousPeriodFacts.none());

        assertThat(below.get(RiskIndicatorCode.LOW_LIQUIDITY)).isTrue();
        assertThat(atThreshold.get(RiskIndicatorCode.LOW_LIQUIDITY)).isFalse();
    }

    @Test
    void weakInterestCoverageTriggersOnlyStrictlyBelowThreshold() {
        Map<RiskIndicatorCode, Boolean> below = evaluator.evaluate(
                ratiosWith(FinancialRatioCode.INTEREST_COVERAGE, "1.49"), Map.of(), Map.of(), PreviousPeriodFacts.none());
        Map<RiskIndicatorCode, Boolean> atThreshold = evaluator.evaluate(
                ratiosWith(FinancialRatioCode.INTEREST_COVERAGE, "1.50"), Map.of(), Map.of(), PreviousPeriodFacts.none());

        assertThat(below.get(RiskIndicatorCode.WEAK_INTEREST_COVERAGE)).isTrue();
        assertThat(atThreshold.get(RiskIndicatorCode.WEAK_INTEREST_COVERAGE)).isFalse();
    }

    @Test
    void negativeCashFlowUsesNetProfitPlusDepreciationProxy() {
        Map<DerivedFinancialFactCode, BigDecimal> facts = factsOf(DerivedFinancialFactCode.NET_PROFIT, "-60000.00");
        Map<FinancialLineItemCode, BigDecimal> raw = rawOf(FinancialLineItemCode.DEPRECIATION_AMORTIZATION, "40000.00");

        Map<RiskIndicatorCode, Boolean> negative = evaluator.evaluate(Map.of(), facts, raw, PreviousPeriodFacts.none());

        assertThat(negative.get(RiskIndicatorCode.NEGATIVE_CASH_FLOW)).isTrue();

        facts.put(DerivedFinancialFactCode.NET_PROFIT, new BigDecimal("-30000.00"));
        Map<RiskIndicatorCode, Boolean> positive = evaluator.evaluate(Map.of(), facts, raw, PreviousPeriodFacts.none());

        assertThat(positive.get(RiskIndicatorCode.NEGATIVE_CASH_FLOW)).isFalse();
    }

    @Test
    void negativeCashFlowIsSkippedWhenAnInputIsMissing() {
        Map<DerivedFinancialFactCode, BigDecimal> facts = factsOf(DerivedFinancialFactCode.NET_PROFIT, "-60000.00");

        Map<RiskIndicatorCode, Boolean> result = evaluator.evaluate(Map.of(), facts, Map.of(), PreviousPeriodFacts.none());

        assertThat(result).doesNotContainKey(RiskIndicatorCode.NEGATIVE_CASH_FLOW);
    }

    @Test
    void decliningRevenueAndEbitdaCompareAgainstThePreviousPeriod() {
        Map<FinancialLineItemCode, BigDecimal> raw = rawOf(FinancialLineItemCode.REVENUE, "900000.00");
        Map<DerivedFinancialFactCode, BigDecimal> facts = factsOf(DerivedFinancialFactCode.EBITDA, "250000.00");
        PreviousPeriodFacts previous =
                new PreviousPeriodFacts(Optional.of(new BigDecimal("1000000.00")), Optional.of(new BigDecimal("290000.00")));

        Map<RiskIndicatorCode, Boolean> result = evaluator.evaluate(Map.of(), facts, raw, previous);

        assertThat(result.get(RiskIndicatorCode.DECLINING_REVENUE)).isTrue();
        assertThat(result.get(RiskIndicatorCode.DECLINING_EBITDA)).isTrue();
    }

    @Test
    void growingRevenueAndEbitdaDoNotTriggerTheDecliningIndicators() {
        Map<FinancialLineItemCode, BigDecimal> raw = rawOf(FinancialLineItemCode.REVENUE, "1100000.00");
        Map<DerivedFinancialFactCode, BigDecimal> facts = factsOf(DerivedFinancialFactCode.EBITDA, "300000.00");
        PreviousPeriodFacts previous =
                new PreviousPeriodFacts(Optional.of(new BigDecimal("1000000.00")), Optional.of(new BigDecimal("290000.00")));

        Map<RiskIndicatorCode, Boolean> result = evaluator.evaluate(Map.of(), facts, raw, previous);

        assertThat(result.get(RiskIndicatorCode.DECLINING_REVENUE)).isFalse();
        assertThat(result.get(RiskIndicatorCode.DECLINING_EBITDA)).isFalse();
    }

    @Test
    void decliningIndicatorsAreSkippedWithNoPreviousPeriod() {
        Map<FinancialLineItemCode, BigDecimal> raw = rawOf(FinancialLineItemCode.REVENUE, "900000.00");
        Map<DerivedFinancialFactCode, BigDecimal> facts = factsOf(DerivedFinancialFactCode.EBITDA, "250000.00");

        Map<RiskIndicatorCode, Boolean> result = evaluator.evaluate(Map.of(), facts, raw, PreviousPeriodFacts.none());

        assertThat(result).doesNotContainKeys(RiskIndicatorCode.DECLINING_REVENUE, RiskIndicatorCode.DECLINING_EBITDA);
    }

    @Test
    void missingRatioInputSkipsOnlyThatIndicatorOthersStillEvaluate() {
        Map<FinancialRatioCode, BigDecimal> ratios = ratiosWith(FinancialRatioCode.CURRENT_RATIO, "0.50");

        Map<RiskIndicatorCode, Boolean> result = evaluator.evaluate(ratios, Map.of(), Map.of(), PreviousPeriodFacts.none());

        assertThat(result).doesNotContainKeys(RiskIndicatorCode.HIGH_LEVERAGE, RiskIndicatorCode.WEAK_INTEREST_COVERAGE);
        assertThat(result.get(RiskIndicatorCode.LOW_LIQUIDITY)).isTrue();
    }

    @Test
    void emptyInputsProduceNoIndicators() {
        Map<RiskIndicatorCode, Boolean> result = evaluator.evaluate(Map.of(), Map.of(), Map.of(), PreviousPeriodFacts.none());

        assertThat(result).isEmpty();
    }
}
