package com.opencredit.platform.loan.strategy;

import com.opencredit.platform.loan.dto.EmploymentType;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.dto.PersonalLoanRequest;
import com.opencredit.platform.loan.dto.PersonalLoanResponse;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.support.EmiCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * EmiCalculator is mocked so the resulting FOIR can be pinned to exact boundary
 * values (0.50 / 0.60) regardless of the real amortisation formula's rounding.
 */
@ExtendWith(MockitoExtension.class)
class PersonalLoanStrategyTest {

    @Mock
    private EmiCalculator emiCalculator;

    private PersonalLoanRequest request(EmploymentType employmentType, BigDecimal monthlyIncome, BigDecimal existingEmi) {
        PersonalLoanRequest request = new PersonalLoanRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setRequestedAmount(new BigDecimal("500000"));
        request.setTenureMonths(60);
        request.setEmploymentType(employmentType);
        request.setMonthlyIncome(monthlyIncome);
        request.setExistingEmi(existingEmi);
        return request;
    }

    @Test
    void approvesWhenFoirIsExactlyAtFiftyPercentThreshold() {
        when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("4000"));
        PersonalLoanRequest request = request(EmploymentType.SALARIED, new BigDecimal("10000"), new BigDecimal("1000"));
        // FOIR = (1000 + 4000) / 10000 = 0.5000

        PersonalLoanResponse response = (PersonalLoanResponse) new PersonalLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getFoir()).isEqualByComparingTo("0.5000");
        assertThat(response.getDecision()).isEqualTo(DecisionStatus.APPROVED);
        assertThat(response.getApprovedAmount()).isEqualByComparingTo(request.getRequestedAmount());
    }

    @Test
    void refersWhenFoirIsExactlySixtyPercentThreshold() {
        when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("5000"));
        PersonalLoanRequest request = request(EmploymentType.SALARIED, new BigDecimal("10000"), new BigDecimal("1000"));
        // FOIR = (1000 + 5000) / 10000 = 0.6000

        LoanResponse response = new PersonalLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getDecision()).isEqualTo(DecisionStatus.REFERRED);
        assertThat(response.getReasons()).isNotEmpty();
    }

    @Test
    void declinesWhenFoirExceedsSixtyPercentThreshold() {
        when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("5001"));
        PersonalLoanRequest request = request(EmploymentType.SALARIED, new BigDecimal("10000"), new BigDecimal("1000"));
        // FOIR = (1000 + 5001) / 10000 = 0.6001

        LoanResponse response = new PersonalLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getDecision()).isEqualTo(DecisionStatus.DECLINED);
        assertThat(response.getApprovedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void selfEmployedUsesHigherInterestRate() {
        when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("1000"));
        PersonalLoanRequest request = request(EmploymentType.SELF_EMPLOYED, new BigDecimal("50000"), BigDecimal.ZERO);

        PersonalLoanResponse response = (PersonalLoanResponse) new PersonalLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getInterestRate()).isEqualByComparingTo("13.5");
    }

    @Test
    void salariedUsesLowerInterestRate() {
        when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("1000"));
        PersonalLoanRequest request = request(EmploymentType.SALARIED, new BigDecimal("50000"), BigDecimal.ZERO);

        PersonalLoanResponse response = (PersonalLoanResponse) new PersonalLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getInterestRate()).isEqualByComparingTo("11.5");
    }
}
