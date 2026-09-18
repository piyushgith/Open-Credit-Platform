package com.opencredit.platform.financial.dto;

import com.opencredit.platform.financial.model.FinancialPeriodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialPeriodResponse {

    private UUID id;
    private String periodLabel;
    private FinancialPeriodType periodType;
    private LocalDate startDate;
    private LocalDate endDate;
}
