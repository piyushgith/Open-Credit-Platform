package com.opencredit.platform.underwriting.dto;

import com.opencredit.platform.underwriting.model.UnderwritingAttemptStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UnderwritingAttemptResponse {

    private UUID id;
    private int cycleNumber;
    private UnderwritingAttemptStatus status;
    private boolean active;
    private Instant startedAt;
    private Instant completedAt;
}
