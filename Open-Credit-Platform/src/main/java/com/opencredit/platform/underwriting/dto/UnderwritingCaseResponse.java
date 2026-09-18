package com.opencredit.platform.underwriting.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UnderwritingCaseResponse {

    private UUID id;
    private UUID applicationId;
    private Instant createdAt;
    private List<UnderwritingAttemptResponse> attempts;
}
