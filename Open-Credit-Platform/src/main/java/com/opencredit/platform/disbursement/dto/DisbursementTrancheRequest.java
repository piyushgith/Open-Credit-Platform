package com.opencredit.platform.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * {@code requestReference} is a client-supplied idempotency key (e.g. a payment/transfer
 * reference); it must be unique per disbursement — see {@code DisbursementTranche}'s
 * {@code UNIQUE (disbursement_id, request_reference)} constraint.
 */
@Data
public class DisbursementTrancheRequest {

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String requestReference;
}
