package com.opencredit.platform.loan.strategy;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.dto.EmploymentType;
import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.dto.PersonalLoanRequest;
import com.opencredit.platform.loan.dto.PersonalLoanResponse;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.support.EmiCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decisioning for {@link ProductType#PERSONAL} based on FOIR (fixed obligation to
 * income ratio): {@code (existingEmi + newEmi) / monthlyIncome}.
 */
@Component
public class PersonalLoanStrategy implements LoanProcessingStrategy {

    private static final BigDecimal SALARIED_RATE = new BigDecimal("11.5");
    private static final BigDecimal SELF_EMPLOYED_RATE = new BigDecimal("13.5");

    private static final BigDecimal FOIR_APPROVE_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal FOIR_REFER_THRESHOLD = new BigDecimal("0.60");
    private static final int FOIR_SCALE = 4;

    private final EmiCalculator emiCalculator;

    public PersonalLoanStrategy(EmiCalculator emiCalculator) {
        this.emiCalculator = emiCalculator;
    }

    @Override
    public ProductType getProductType() {
        return ProductType.PERSONAL;
    }

    @Override
    public LoanResponse processLoan(LoanRequest loanRequest) {
        PersonalLoanRequest request = (PersonalLoanRequest) loanRequest;

        BigDecimal interestRate = request.getEmploymentType() == EmploymentType.SALARIED
                ? SALARIED_RATE
                : SELF_EMPLOYED_RATE;

        BigDecimal emi = emiCalculator.calculate(request.getRequestedAmount(), interestRate, request.getTenureMonths());

        BigDecimal foir = request.getExistingEmi().add(emi)
                .divide(request.getMonthlyIncome(), FOIR_SCALE, RoundingMode.HALF_UP);

        PersonalLoanResponse response = new PersonalLoanResponse();
        response.setInterestRate(interestRate);
        response.setMonthlyEmi(emi);
        response.setTenureMonths(request.getTenureMonths());
        response.setFoir(foir);

        if (foir.compareTo(FOIR_APPROVE_THRESHOLD) <= 0) {
            response.setDecision(DecisionStatus.APPROVED);
            response.setApprovedAmount(request.getRequestedAmount());
            response.addReason("FOIR " + foir + " is within the 0.50 approval threshold");
        } else if (foir.compareTo(FOIR_REFER_THRESHOLD) <= 0) {
            response.setDecision(DecisionStatus.REFERRED);
            response.setApprovedAmount(request.getRequestedAmount());
            response.addReason("FOIR " + foir + " exceeds 0.50 but is within the 0.60 referral threshold");
        } else {
            response.setDecision(DecisionStatus.DECLINED);
            response.setApprovedAmount(BigDecimal.ZERO);
            response.addReason("FOIR " + foir + " exceeds the 0.60 maximum allowed");
        }

        return response;
    }
}
