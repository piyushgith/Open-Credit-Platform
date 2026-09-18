package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class VehicleLoanRequest extends LoanRequest {

    @NotBlank
    private String vehicleMake;

    @NotNull
    @Positive
    private BigDecimal vehiclePrice;

    @NotNull
    private BigDecimal downPayment;

    @NotNull
    private VehicleCondition condition;

    @Override
    public ProductType getProductType() {
        return ProductType.VEHICLE;
    }
}
