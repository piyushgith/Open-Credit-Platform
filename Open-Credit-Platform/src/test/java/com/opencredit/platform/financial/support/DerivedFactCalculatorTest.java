package com.opencredit.platform.financial.support;

import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
import com.opencredit.platform.financial.model.FinancialLineItemCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedFactCalculatorTest {

    private final DerivedFactCalculator calculator = new DerivedFactCalculator();

    private static Map<FinancialLineItemCode, BigDecimal> fullRawValues() {
        Map<FinancialLineItemCode, BigDecimal> raw = new EnumMap<>(FinancialLineItemCode.class);
        raw.put(FinancialLineItemCode.CURRENT_ASSETS, new BigDecimal("500000"));
        raw.put(FinancialLineItemCode.NON_CURRENT_ASSETS, new BigDecimal("300000"));
        raw.put(FinancialLineItemCode.CURRENT_LIABILITIES, new BigDecimal("200000"));
        raw.put(FinancialLineItemCode.NON_CURRENT_LIABILITIES, new BigDecimal("150000"));
        raw.put(FinancialLineItemCode.LONG_TERM_DEBT, new BigDecimal("100000"));
        raw.put(FinancialLineItemCode.SHORT_TERM_DEBT, new BigDecimal("50000"));
        raw.put(FinancialLineItemCode.REVENUE, new BigDecimal("1000000"));
        raw.put(FinancialLineItemCode.COST_OF_GOODS_SOLD, new BigDecimal("600000"));
        raw.put(FinancialLineItemCode.OPERATING_EXPENSES, new BigDecimal("150000"));
        raw.put(FinancialLineItemCode.DEPRECIATION_AMORTIZATION, new BigDecimal("40000"));
        raw.put(FinancialLineItemCode.INTEREST_EXPENSE, new BigDecimal("20000"));
        raw.put(FinancialLineItemCode.TAX_EXPENSE, new BigDecimal("30000"));
        return raw;
    }

    @Test
    void calculatesAllFactsWhenEveryRequiredInputIsPresent() {
        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(fullRawValues());

        assertThat(facts).hasSize(8);
        assertThat(facts.get(DerivedFinancialFactCode.TOTAL_ASSETS)).isEqualByComparingTo("800000.00");
        assertThat(facts.get(DerivedFinancialFactCode.TOTAL_LIABILITIES)).isEqualByComparingTo("350000.00");
        assertThat(facts.get(DerivedFinancialFactCode.WORKING_CAPITAL)).isEqualByComparingTo("300000.00");
        assertThat(facts.get(DerivedFinancialFactCode.TOTAL_DEBT)).isEqualByComparingTo("150000.00");
        assertThat(facts.get(DerivedFinancialFactCode.GROSS_PROFIT)).isEqualByComparingTo("400000.00");
        assertThat(facts.get(DerivedFinancialFactCode.OPERATING_PROFIT)).isEqualByComparingTo("250000.00");
        assertThat(facts.get(DerivedFinancialFactCode.EBITDA)).isEqualByComparingTo("290000.00");
        assertThat(facts.get(DerivedFinancialFactCode.NET_PROFIT)).isEqualByComparingTo("200000.00");
    }

    @Test
    void missingLevelOneInputSkipsOnlyItsOwnDependents() {
        Map<FinancialLineItemCode, BigDecimal> raw = fullRawValues();
        raw.remove(FinancialLineItemCode.OPERATING_EXPENSES);

        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(raw);

        assertThat(facts).containsKeys(
                DerivedFinancialFactCode.TOTAL_ASSETS,
                DerivedFinancialFactCode.TOTAL_LIABILITIES,
                DerivedFinancialFactCode.WORKING_CAPITAL,
                DerivedFinancialFactCode.TOTAL_DEBT,
                DerivedFinancialFactCode.GROSS_PROFIT);
        assertThat(facts).doesNotContainKeys(
                DerivedFinancialFactCode.OPERATING_PROFIT,
                DerivedFinancialFactCode.EBITDA,
                DerivedFinancialFactCode.NET_PROFIT);
    }

    @Test
    void missingRawInputForALevelOneFactDoesNotThrow() {
        Map<FinancialLineItemCode, BigDecimal> raw = fullRawValues();
        raw.remove(FinancialLineItemCode.CURRENT_ASSETS);

        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(raw);

        assertThat(facts).doesNotContainKeys(
                DerivedFinancialFactCode.TOTAL_ASSETS,
                DerivedFinancialFactCode.WORKING_CAPITAL);
        assertThat(facts).containsKey(DerivedFinancialFactCode.GROSS_PROFIT);
    }

    @Test
    void zeroIsTreatedAsAPresentValueNotAMissingOne() {
        Map<FinancialLineItemCode, BigDecimal> raw = new EnumMap<>(FinancialLineItemCode.class);
        raw.put(FinancialLineItemCode.CURRENT_ASSETS, new BigDecimal("100000"));
        raw.put(FinancialLineItemCode.NON_CURRENT_ASSETS, BigDecimal.ZERO);

        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(raw);

        assertThat(facts.get(DerivedFinancialFactCode.TOTAL_ASSETS)).isEqualByComparingTo("100000.00");
    }

    @Test
    void negativeResultsPropagateWithoutBeingClampedToZero() {
        Map<FinancialLineItemCode, BigDecimal> raw = new EnumMap<>(FinancialLineItemCode.class);
        raw.put(FinancialLineItemCode.REVENUE, new BigDecimal("100000"));
        raw.put(FinancialLineItemCode.COST_OF_GOODS_SOLD, new BigDecimal("150000"));
        raw.put(FinancialLineItemCode.OPERATING_EXPENSES, new BigDecimal("10000"));
        raw.put(FinancialLineItemCode.DEPRECIATION_AMORTIZATION, new BigDecimal("5000"));
        raw.put(FinancialLineItemCode.INTEREST_EXPENSE, new BigDecimal("2000"));
        raw.put(FinancialLineItemCode.TAX_EXPENSE, new BigDecimal("1000"));

        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(raw);

        assertThat(facts.get(DerivedFinancialFactCode.GROSS_PROFIT)).isEqualByComparingTo("-50000.00");
        assertThat(facts.get(DerivedFinancialFactCode.OPERATING_PROFIT)).isEqualByComparingTo("-60000.00");
        assertThat(facts.get(DerivedFinancialFactCode.EBITDA)).isEqualByComparingTo("-55000.00");
        assertThat(facts.get(DerivedFinancialFactCode.NET_PROFIT)).isEqualByComparingTo("-63000.00");
    }

    @Test
    void roundsToTwoDecimalPlacesHalfUp() {
        Map<FinancialLineItemCode, BigDecimal> raw = new EnumMap<>(FinancialLineItemCode.class);
        raw.put(FinancialLineItemCode.CURRENT_ASSETS, new BigDecimal("10.005"));
        raw.put(FinancialLineItemCode.NON_CURRENT_ASSETS, BigDecimal.ZERO);
        raw.put(FinancialLineItemCode.CURRENT_LIABILITIES, BigDecimal.ZERO);

        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(raw);

        assertThat(facts.get(DerivedFinancialFactCode.TOTAL_ASSETS)).isEqualByComparingTo("10.01");
        assertThat(facts.get(DerivedFinancialFactCode.TOTAL_ASSETS).scale()).isEqualTo(2);
        assertThat(facts.get(DerivedFinancialFactCode.WORKING_CAPITAL)).isEqualByComparingTo("10.01");
    }

    @Test
    void emptyRawValuesProduceNoFacts() {
        Map<DerivedFinancialFactCode, BigDecimal> facts = calculator.calculate(new EnumMap<>(FinancialLineItemCode.class));

        assertThat(facts).isEmpty();
    }
}
