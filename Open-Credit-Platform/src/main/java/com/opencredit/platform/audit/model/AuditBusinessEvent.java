package com.opencredit.platform.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One named business event (see {@link AuditEventType}), written by {@code AuditService} in the
 * same transaction as the state change it describes — never by a listener. Append-only: nothing in
 * this codebase updates or deletes a row here.
 */
@Entity
@Table(name = "audit_business_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditBusinessEvent {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AuditEventType eventType;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "details")
    private String details;
}
