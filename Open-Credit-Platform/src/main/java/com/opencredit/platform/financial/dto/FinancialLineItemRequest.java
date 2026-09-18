package com.opencredit.platform.financial.dto;

import com.opencredit.platform.financial.model.FinancialLineItemCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinancialLineItemRequest {

    @NotNull
    private FinancialLineItemCode lineItemCode;

    @NotNull
    private BigDecimal value;
}
