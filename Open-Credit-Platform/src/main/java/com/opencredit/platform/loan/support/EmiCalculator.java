package com.opencredit.platform.loan.support;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Standard reducing-balance EMI (equated monthly installment) calculation, shared by
 * every strategy so the amortisation formula is implemented and tested exactly once.
 *
 * <pre>
 * EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
 * r   = annualInterestRatePercent / 12 / 100   (monthly rate)
 * n   = tenureMonths
 * </pre>
 *
 * <p>Intermediate rate math is carried at {@link #RATE_MATH_CONTEXT} precision to avoid
 * a non-terminating decimal from tripping {@link ArithmeticException}; the final EMI is
 * rounded to 2 decimal places ({@link RoundingMode#HALF_UP}) as money.
 */
@Component
public class EmiCalculator {

    private static final MathContext RATE_MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
    private static final int MONEY_SCALE = 2;

    public BigDecimal calculate(BigDecimal principal, BigDecimal annualInterestRatePercent, int tenureMonths) {
        BigDecimal monthlyRate = annualInterestRatePercent
                .divide(BigDecimal.valueOf(1200), RATE_MATH_CONTEXT);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal compounded = onePlusR.pow(tenureMonths, RATE_MATH_CONTEXT);

        BigDecimal numerator = principal.multiply(monthlyRate).multiply(compounded);
        BigDecimal denominator = compounded.subtract(BigDecimal.ONE);

        return numerator.divide(denominator, MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
