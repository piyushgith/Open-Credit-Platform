package com.opencredit.platform.financial.dto;

import com.opencredit.platform.financial.model.DerivedFinancialFactCode;
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
public class DerivedFinancialFactResponse {

    private UUID id;
    private DerivedFinancialFactCode factCode;
    private BigDecimal value;
}
