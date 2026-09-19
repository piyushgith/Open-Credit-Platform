package com.opencredit.platform.audit.dto;

import com.opencredit.platform.audit.model.AuditEventType;
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
public class AuditBusinessEventResponse {

    private UUID id;
    private AuditEventType eventType;
    private String entityType;
    private UUID entityId;
    private String actorUsername;
    private Instant occurredAt;
    private String requestId;
    private String correlationId;
    private String details;
}
