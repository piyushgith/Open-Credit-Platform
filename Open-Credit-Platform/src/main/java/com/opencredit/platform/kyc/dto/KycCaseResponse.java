package com.opencredit.platform.kyc.dto;

import com.opencredit.platform.kyc.model.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class KycCaseResponse {

    private UUID id;
    private UUID applicationId;
    private KycStatus status;
    private String remarks;
    private Instant createdAt;
    private Instant updatedAt;
}
