package com.opencredit.platform.financial.dto;

import com.opencredit.platform.financial.model.FinancialLineItemCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialLineItemResponse {

    private UUID id;
    private FinancialLineItemCode lineItemCode;
    private BigDecimal value;
}
