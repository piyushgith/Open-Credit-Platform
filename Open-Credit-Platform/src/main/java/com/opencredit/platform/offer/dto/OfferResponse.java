package com.opencredit.platform.offer.dto;

import com.opencredit.platform.offer.model.OfferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferResponse {

    private UUID id;
    private String applicationReference;
    private BigDecimal offerAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal monthlyEmi;
    private OfferStatus status;
    private Instant createdAt;
}
