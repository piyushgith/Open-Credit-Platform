package com.opencredit.platform.disbursement.dto;

import com.opencredit.platform.disbursement.model.DisbursementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementResponse {

    private String applicationReference;
    private BigDecimal sanctionedAmount;
    private BigDecimal disbursedTotal;
    private DisbursementStatus status;
    private List<DisbursementTrancheDetail> tranches;
}
