package com.opencredit.platform.financial.support;

import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import com.opencredit.platform.financial.model.FinancialRatioCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RatioCalculatorTest {

    private final RatioCalculator calculator = new RatioCalculator();

    private static Map<FinancialLineItemCode, BigDecimal> fullRawValues() {
        Map<FinancialLineItemCode, BigDecimal> raw = new EnumMap<>(FinancialLineItemCode.class);
        raw.put(FinancialLineItemCode.CURRENT_ASSETS, new BigDecimal("500000"));
        raw.put(FinancialLineItemCode.NON_CURRENT_ASSETS, new BigDecimal("300000"));
        raw.put(FinancialLineItemCode.CURRENT_LIABILITIES, new BigDecimal("200000"));
        raw.put(FinancialLineItemCode.NON_CURRENT_LIABILITIES, new BigDecimal("150000"));
        raw.put(FinancialLineItemCode.EQUITY, new BigDecimal("400000"));
        raw.put(FinancialLineItemCode.REVENUE, new BigDecimal("1000000"));
        raw.put(FinancialLineItemCode.COST_OF_GOODS_SOLD, new BigDecimal("600000"));
        raw.put(FinancialLineItemCode.OPERATING_EXPENSES, new BigDecimal("150000"));
        raw.put(FinancialLineItemCode.DEPRECIATION_AMORTIZATION, new BigDecimal("40000"));
        raw.put(FinancialLineItemCode.INTEREST_EXPENSE, new BigDecimal("20000"));
        raw.put(FinancialLineItemCode.TAX_EXPENSE, new BigDecimal("30000"));
        raw.put(FinancialLineItemCode.LONG_TERM_DEBT, new BigDecimal("100000"));
        raw.put(FinancialLineItemCode.SHORT_TERM_DEBT, new BigDecimal("50000"));
        raw.put(FinancialLineItemCode.INVENTORY, new BigDecimal("100000"));
        return raw;
    }

    /** Matches what {@link DerivedFactCalculator} produces from {@link #fullRawValues()}. */
    private static Map<DerivedFinancialFactCode, BigDecimal> fullFacts() {
        Map<DerivedFinancialFactCode, BigDecimal> facts = new EnumMap<>(DerivedFinancialFactCode.class);
        facts.put(DerivedFinancialFactCode.TOTAL_ASSETS, new BigDecimal("800000.00"));
        facts.put(DerivedFinancialFactCode.TOTAL_LIABILITIES, new BigDecimal("350000.00"));
        facts.put(DerivedFinancialFactCode.WORKING_CAPITAL, new BigDecimal("300000.00"));
        facts.put(DerivedFinancialFactCode.TOTAL_DEBT, new BigDecimal("150000.00"));
        facts.put(DerivedFinancialFactCode.GROSS_PROFIT, new BigDecimal("400000.00"));
        facts.put(DerivedFinancialFactCode.OPERATING_PROFIT, new BigDecimal("250000.00"));
        facts.put(DerivedFinancialFactCode.EBITDA, new BigDecimal("290000.00"));
        facts.put(DerivedFinancialFactCode.NET_PROFIT, new BigDecimal("200000.00"));
        return facts;
    }

    @Test
    void calculatesAllRatiosWhenEveryRequiredInputIsPresent() {
        Map<FinancialRatioCode, BigDecimal> ratios = calculator.calculate(fullRawValues(), fullFacts());

        assertThat(ratios).hasSize(11);
        assertThat(ratios.get(FinancialRatioCode.CURRENT_RATIO)).isEqualByComparingTo("2.5000");
        assertThat(ratios.get(FinancialRatioCode.QUICK_RATIO)).isEqualByComparingTo("2.0000");
        assertThat(ratios.get(FinancialRatioCode.DEBT_TO_EQUITY)).isEqualByComparingTo("0.3750");
        assertThat(ratios.get(FinancialRatioCode.DEBT_TO_EBITDA)).isEqualByComparingTo("0.5172");
        assertThat(ratios.get(FinancialRatioCode.INTEREST_COVERAGE)).isEqualByComparingTo("14.5000");
        assertThat(ratios.get(FinancialRatioCode.GROSS_MARGIN)).isEqualByComparingTo("0.4000");
        assertThat(ratios.get(FinancialRatioCode.EBITDA_MARGIN)).isEqualByComparingTo("0.2900");
        assertThat(ratios.get(FinancialRatioCode.NET_MARGIN)).isEqualByComparingTo("0.2000");
        assertThat(ratios.get(FinancialRatioCode.ROA)).isEqualByComparingTo("0.2500");
        assertThat(ratios.get(FinancialRatioCode.ROE)).isEqualByComparingTo("0.5000");
        assertThat(ratios.get(FinancialRatioCode.DSCR)).isEqualByComparingTo("4.1429");
    }

    @Test
    void zeroDenominatorSkipsOnlyThatRatio() {
        Map<FinancialLineItemCode, BigDecimal> raw = fullRawValues();
        raw.put(FinancialLineItemCode.CURRENT_LIABILITIES, BigDecimal.ZERO);

        Map<FinancialRatioCode, BigDecimal> ratios = calculator.calculate(raw, fullFacts());

        assertThat(ratios).doesNotContainKeys(FinancialRatioCode.CURRENT_RATIO, FinancialRatioCode.QUICK_RATIO);
        assertThat(ratios.get(FinancialRatioCode.DEBT_TO_EQUITY)).isEqualByComparingTo("0.3750");
        assertThat(ratios).hasSize(9);
    }

    @Test
    void missingInputSkipsOnlyDependentRatios() {
        Map<FinancialLineItemCode, BigDecimal> raw = fullRawValues();
        raw.remove(FinancialLineItemCode.EQUITY);

        Map<FinancialRatioCode, BigDecimal> ratios = calculator.calculate(raw, fullFacts());

        assertThat(ratios).doesNotContainKeys(FinancialRatioCode.DEBT_TO_EQUITY, FinancialRatioCode.ROE);
        assertThat(ratios.get(FinancialRatioCode.CURRENT_RATIO)).isEqualByComparingTo("2.5000");
        assertThat(ratios.get(FinancialRatioCode.ROA)).isEqualByComparingTo("0.2500");
    }

    @Test
    void missingDerivedFactSkipsOnlyDependentRatios() {
        Map<DerivedFinancialFactCode, BigDecimal> facts = fullFacts();
        facts.remove(DerivedFinancialFactCode.EBITDA);

        Map<FinancialRatioCode, BigDecimal> ratios = calculator.calculate(fullRawValues(), facts);

        assertThat(ratios).doesNotContainKeys(FinancialRatioCode.DEBT_TO_EBITDA,
                FinancialRatioCode.INTEREST_COVERAGE, FinancialRatioCode.EBITDA_MARGIN, FinancialRatioCode.DSCR);
        assertThat(ratios.get(FinancialRatioCode.GROSS_MARGIN)).isEqualByComparingTo("0.4000");
    }

    @Test
    void negativeResultsPropagateWithoutBeingClampedToZero() {
        Map<DerivedFinancialFactCode, BigDecimal> facts = fullFacts();
        facts.put(DerivedFinancialFactCode.NET_PROFIT, new BigDecimal("-50000.00"));

        Map<FinancialRatioCode, BigDecimal> ratios = calculator.calculate(fullRawValues(), facts);

        assertThat(ratios.get(FinancialRatioCode.NET_MARGIN)).isEqualByComparingTo("-0.0500");
        assertThat(ratios.get(FinancialRatioCode.ROA)).isEqualByComparingTo("-0.0625");
        assertThat(ratios.get(FinancialRatioCode.ROE)).isEqualByComparingTo("-0.1250");
    }

    @Test
    void roundsToFourDecimalPlacesHalfUp() {
        Map<FinancialRatioCode, BigDecimal> ratios = calculator.calculate(fullRawValues(), fullFacts());

        assertThat(ratios.get(FinancialRatioCode.DSCR).scale()).isEqualTo(4);
        assertThat(ratios.get(FinancialRatioCode.DEBT_TO_EBITDA)).isEqualByComparingTo("0.5172");
    }

    @Test
    void emptyInputsProduceNoRatios() {
        Map<FinancialRatioCode, BigDecimal> ratios = calculator.calculate(
                new EnumMap<>(FinancialLineItemCode.class), new EnumMap<>(DerivedFinancialFactCode.class));

        assertThat(ratios).isEmpty();
    }
}
