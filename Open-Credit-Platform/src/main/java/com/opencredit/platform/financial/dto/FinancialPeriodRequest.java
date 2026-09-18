package com.opencredit.platform.financial.dto;

import com.opencredit.platform.financial.model.FinancialPeriodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FinancialPeriodRequest {

    @NotBlank
    private String periodLabel;

    @NotNull
    private FinancialPeriodType periodType;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;
}
