package com.opencredit.platform.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialStatementResponse {

    private UUID id;
    private UUID applicationId;
    private FinancialPeriodResponse period;
    private List<FinancialLineItemResponse> lineItems;
    private Instant submittedAt;
}
