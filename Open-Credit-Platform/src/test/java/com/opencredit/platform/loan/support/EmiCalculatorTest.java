package com.opencredit.platform.loan.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EmiCalculatorTest {

    private final EmiCalculator calculator = new EmiCalculator();

    @Test
    void calculatesKnownAmortisationValue() {
        // P=100000, annual rate=12% (1% monthly), n=12 -> standard textbook EMI ~= 8884.88
        BigDecimal emi = calculator.calculate(new BigDecimal("100000"), new BigDecimal("12"), 12);

        assertThat(emi).isEqualByComparingTo("8884.88");
    }

    @Test
    void zeroRateSplitsPrincipalEvenlyAcrossTenure() {
        BigDecimal emi = calculator.calculate(new BigDecimal("120000"), BigDecimal.ZERO, 12);

        assertThat(emi).isEqualByComparingTo("10000.00");
    }

    @Test
    void singleMonthTenureRepaysEntirePrincipalPlusInterest() {
        BigDecimal emi = calculator.calculate(new BigDecimal("10000"), new BigDecimal("12"), 1);

        // one month at 1% monthly rate: 10000 * 1.01
        assertThat(emi).isEqualByComparingTo("10100.00");
    }

    @Test
    void roundsToTwoDecimalPlaces() {
        BigDecimal emi = calculator.calculate(new BigDecimal("500000"), new BigDecimal("11.5"), 60);

        assertThat(emi.scale()).isEqualTo(2);
    }

    @Test
    void doesNotThrowOnNonTerminatingDivision() {
        // A rate/tenure combination whose raw division would be a repeating decimal.
        BigDecimal emi = calculator.calculate(new BigDecimal("333333"), new BigDecimal("7.25"), 37);

        assertThat(emi).isPositive();
    }
}
