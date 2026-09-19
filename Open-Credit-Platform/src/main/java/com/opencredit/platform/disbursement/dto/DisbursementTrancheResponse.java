package com.opencredit.platform.disbursement.dto;

import com.opencredit.platform.disbursement.model.DisbursementStatus;
import com.opencredit.platform.loan.model.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code applicationStatus} is set by {@code LoanApplicationService} after it decides whether this
 * tranche completed the disbursement (and therefore moved the application to {@code DISBURSED}) —
 * {@code DisbursementService} itself has no reason to know about {@code ApplicationStatus}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementTrancheResponse {

    private String applicationReference;
    private ApplicationStatus applicationStatus;
    private UUID trancheId;
    private Integer trancheNumber;
    private BigDecimal amount;
    private String requestReference;
    private BigDecimal disbursedTotal;
    private BigDecimal sanctionedAmount;
    private DisbursementStatus disbursementStatus;
    private Instant createdAt;
}
