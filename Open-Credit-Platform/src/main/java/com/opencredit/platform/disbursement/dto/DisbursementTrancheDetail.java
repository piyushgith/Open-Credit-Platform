package com.opencredit.platform.disbursement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementTrancheDetail {

    private Integer trancheNumber;
    private BigDecimal amount;
    private String requestReference;
    private Instant createdAt;
}
