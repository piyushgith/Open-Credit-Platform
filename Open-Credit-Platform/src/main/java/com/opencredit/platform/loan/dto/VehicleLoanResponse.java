package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class VehicleLoanResponse extends LoanResponse {

    /** requestedAmount / vehiclePrice (or approvedAmount / vehiclePrice once capped). */
    private BigDecimal loanToValue;

    private BigDecimal downPayment;

    @Override
    public ProductType getProductType() {
        return ProductType.VEHICLE;
    }
}
