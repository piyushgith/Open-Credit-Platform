package com.opencredit.platform.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One field-level change. Deliberately scoped to the status (and, for {@code Disbursement}, the
 * running-total) fields that actually gate a business transition — see the Week 10 plan's
 * "Data-change audit" section for why this is not a blanket entity/field-diff mechanism over every
 * column of every entity.
 */
@Entity
@Table(name = "audit_data_change")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditDataChange {

    @Id
    private UUID id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "field", nullable = false)
    private String field;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;
}
