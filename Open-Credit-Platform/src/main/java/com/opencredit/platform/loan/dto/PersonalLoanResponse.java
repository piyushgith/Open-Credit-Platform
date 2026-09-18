package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalLoanResponse extends LoanResponse {

    /** Fixed obligation to income ratio: (existingEmi + newEmi) / monthlyIncome. */
    private BigDecimal foir;

    @Override
    public ProductType getProductType() {
        return ProductType.PERSONAL;
    }
}
