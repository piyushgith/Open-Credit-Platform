package com.opencredit.platform.loan.strategy;

import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.dto.VehicleCondition;
import com.opencredit.platform.loan.dto.VehicleLoanRequest;
import com.opencredit.platform.loan.dto.VehicleLoanResponse;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class VehicleLoanStrategyTest {

    @Mock
    private EmiCalculator emiCalculator;

    private VehicleLoanRequest request(VehicleCondition condition, String vehiclePrice, String requestedAmount, String downPayment) {
        VehicleLoanRequest request = new VehicleLoanRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setTenureMonths(48);
        request.setCondition(condition);
        request.setVehiclePrice(new BigDecimal(vehiclePrice));
        request.setRequestedAmount(new BigDecimal(requestedAmount));
        request.setDownPayment(new BigDecimal(downPayment));
        return request;
    }

    @Test
    void approvesNewVehicleWithinMaxLtv() {
        lenient().when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("2000.00"));
        VehicleLoanRequest request = request(VehicleCondition.NEW, "1000000", "800000", "200000");

        VehicleLoanResponse response = (VehicleLoanResponse) new VehicleLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getDecision()).isEqualTo(DecisionStatus.APPROVED);
        assertThat(response.getApprovedAmount()).isEqualByComparingTo("800000");
        assertThat(response.getLoanToValue()).isEqualByComparingTo("0.8000");
        assertThat(response.getInterestRate()).isEqualByComparingTo("9.5");
    }

    @Test
    void capsApprovedAmountWhenUsedVehicleExceedsMaxLtv() {
        lenient().when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("18000.00"));
        VehicleLoanRequest request = request(VehicleCondition.USED, "1000000", "900000", "150000");
        // requested LTV = 0.90, max for USED = 0.70 -> capped to 700000

        VehicleLoanResponse response = (VehicleLoanResponse) new VehicleLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getDecision()).isEqualTo(DecisionStatus.REFERRED);
        assertThat(response.getApprovedAmount()).isEqualByComparingTo("700000.00");
        assertThat(response.getLoanToValue()).isEqualByComparingTo("0.7000");
        assertThat(response.getInterestRate()).isEqualByComparingTo("12.5");
        assertThat(response.getReasons()).isNotEmpty();
    }

    @Test
    void declinesWhenDownPaymentPlusRequestedAmountIsInsufficient() {
        VehicleLoanRequest request = request(VehicleCondition.NEW, "1000000", "800000", "50000");
        // 800000 + 50000 = 850000 < 1000000

        LoanResponse response = new VehicleLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getDecision()).isEqualTo(DecisionStatus.DECLINED);
        assertThat(response.getApprovedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getReasons()).isNotEmpty();
    }

    @Test
    void allowsRequestedLtvExactlyAtMaxThreshold() {
        lenient().when(emiCalculator.calculate(any(), any(), anyInt())).thenReturn(new BigDecimal("5000.00"));
        VehicleLoanRequest request = request(VehicleCondition.NEW, "1000000", "850000", "150000");
        // requested LTV = 0.85, exactly at the NEW max -> not capped, approved as requested

        VehicleLoanResponse response = (VehicleLoanResponse) new VehicleLoanStrategy(emiCalculator).processLoan(request);

        assertThat(response.getDecision()).isEqualTo(DecisionStatus.APPROVED);
        assertThat(response.getApprovedAmount()).isEqualByComparingTo("850000");
    }
}
