package com.opencredit.platform.financial.dto;

import com.opencredit.platform.financial.model.FinancialCategory;
import com.opencredit.platform.financial.model.FinancialRatioCode;
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
public class FinancialRatioResponse {

    private UUID id;
    private FinancialRatioCode ratioCode;
    private FinancialCategory category;
    private BigDecimal value;
}
