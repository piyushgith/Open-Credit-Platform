package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalLoanRequest extends LoanRequest {

    @NotNull
    @Positive
    @Digits(integer = 17, fraction = 2)
    private BigDecimal monthlyIncome;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 17, fraction = 2)
    private BigDecimal existingEmi;

    @NotNull
    private EmploymentType employmentType;

    @Override
    public ProductType getProductType() {
        return ProductType.PERSONAL;
    }
}
