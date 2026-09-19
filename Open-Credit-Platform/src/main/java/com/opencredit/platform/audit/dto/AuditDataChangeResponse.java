package com.opencredit.platform.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditDataChangeResponse {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private String field;
    private String oldValue;
    private String newValue;
    private String changedBy;
    private Instant changedAt;
    private String requestId;
    private String correlationId;
}
