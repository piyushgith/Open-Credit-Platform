package com.opencredit.platform.offer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * {@code requestedAmount} is optional: when omitted, the offer uses the full amount underwriting
 * already approved. {@code tenureMonths} is always required and is validated against the loan
 * product's bounds (the same bounds enforced at apply time). The interest rate is never accepted
 * here — it is fixed at whatever underwriting already decided.
 */
@Data
public class OfferRequest {

    @NotNull
    @Min(1)
    @Max(600)
    private Integer tenureMonths;

    @Positive
    private BigDecimal requestedAmount;
}
