package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class LoanProductResponse {

    private UUID id;
    private ProductType productType;
    private String name;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer minTenureMonths;
    private Integer maxTenureMonths;
    private boolean active;
}
