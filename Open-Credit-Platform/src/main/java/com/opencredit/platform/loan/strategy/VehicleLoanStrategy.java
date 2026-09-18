package com.opencredit.platform.loan.strategy;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.dto.VehicleCondition;
import com.opencredit.platform.loan.dto.VehicleLoanRequest;
import com.opencredit.platform.loan.dto.VehicleLoanResponse;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.support.EmiCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decisioning for {@link ProductType#VEHICLE} based on LTV (loan to value):
 * {@code requestedAmount / vehiclePrice}.
 */
@Component
public class VehicleLoanStrategy implements LoanProcessingStrategy {

    private static final BigDecimal NEW_RATE = new BigDecimal("9.5");
    private static final BigDecimal USED_RATE = new BigDecimal("12.5");

    private static final BigDecimal NEW_MAX_LTV = new BigDecimal("0.85");
    private static final BigDecimal USED_MAX_LTV = new BigDecimal("0.70");
    private static final int LTV_SCALE = 4;

    private final EmiCalculator emiCalculator;

    public VehicleLoanStrategy(EmiCalculator emiCalculator) {
        this.emiCalculator = emiCalculator;
    }

    @Override
    public ProductType getProductType() {
        return ProductType.VEHICLE;
    }

    @Override
    public LoanResponse processLoan(LoanRequest loanRequest) {
        VehicleLoanRequest request = (VehicleLoanRequest) loanRequest;

        boolean isNew = request.getCondition() == VehicleCondition.NEW;
        BigDecimal interestRate = isNew ? NEW_RATE : USED_RATE;
        BigDecimal maxLtv = isNew ? NEW_MAX_LTV : USED_MAX_LTV;

        VehicleLoanResponse response = new VehicleLoanResponse();
        response.setInterestRate(interestRate);
        response.setTenureMonths(request.getTenureMonths());
        response.setDownPayment(request.getDownPayment());

        BigDecimal totalFundsArranged = request.getDownPayment().add(request.getRequestedAmount());
        if (totalFundsArranged.compareTo(request.getVehiclePrice()) < 0) {
            response.setDecision(DecisionStatus.DECLINED);
            response.setApprovedAmount(BigDecimal.ZERO);
            response.setLoanToValue(BigDecimal.ZERO);
            response.setMonthlyEmi(BigDecimal.ZERO);
            response.addReason("Down payment plus requested amount is less than the vehicle price");
            return response;
        }

        BigDecimal requestedLtv = request.getRequestedAmount()
                .divide(request.getVehiclePrice(), LTV_SCALE, RoundingMode.HALF_UP);

        BigDecimal approvedAmount;
        if (requestedLtv.compareTo(maxLtv) > 0) {
            approvedAmount = maxLtv.multiply(request.getVehiclePrice()).setScale(2, RoundingMode.HALF_UP);
            response.setDecision(DecisionStatus.REFERRED);
            response.addReason("Requested LTV " + requestedLtv + " exceeds the " + maxLtv
                    + " maximum for " + request.getCondition() + " vehicles; amount capped");
        } else {
            approvedAmount = request.getRequestedAmount();
            response.setDecision(DecisionStatus.APPROVED);
            response.addReason("Requested LTV " + requestedLtv + " is within the " + maxLtv
                    + " maximum for " + request.getCondition() + " vehicles");
        }

        response.setApprovedAmount(approvedAmount);
        response.setLoanToValue(approvedAmount.divide(request.getVehiclePrice(), LTV_SCALE, RoundingMode.HALF_UP));
        response.setMonthlyEmi(emiCalculator.calculate(approvedAmount, interestRate, request.getTenureMonths()));

        return response;
    }
}
