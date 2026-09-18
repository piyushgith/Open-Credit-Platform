package com.opencredit.platform.financial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FinancialStatementRequest {

    @NotNull
    @Valid
    private FinancialPeriodRequest period;

    @NotEmpty
    @Valid
    private List<FinancialLineItemRequest> lineItems;
}
