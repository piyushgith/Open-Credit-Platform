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
public class FinancialAnalysisRunResponse {

    private UUID id;
    private UUID statementId;
    private Instant runAt;
    private List<DerivedFinancialFactResponse> facts;
}
