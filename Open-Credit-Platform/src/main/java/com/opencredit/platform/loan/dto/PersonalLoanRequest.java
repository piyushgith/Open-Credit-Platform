package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalLoanRequest extends LoanRequest {

    @NotNull
    @Positive
    private BigDecimal monthlyIncome;

    @NotNull
    private BigDecimal existingEmi;

    @NotNull
    private EmploymentType employmentType;

    @Override
    public ProductType getProductType() {
        return ProductType.PERSONAL;
    }
}
