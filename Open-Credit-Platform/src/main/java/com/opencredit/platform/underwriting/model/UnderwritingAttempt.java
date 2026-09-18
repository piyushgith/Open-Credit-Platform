package com.opencredit.platform.underwriting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One run of underwriting for an {@link UnderwritingCase}. Never mutated once it reaches a
 * terminal {@link UnderwritingAttemptStatus} (enforced by
 * {@link com.opencredit.platform.underwriting.support.UnderwritingAttemptLifecycle}) — a retry
 * creates a new row with the next {@code cycleNumber} instead.
 */
@Entity
@Table(name = "underwriting_attempt")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingAttempt {

    @Id
    private UUID id;

    @Column(name = "underwriting_case_id", nullable = false)
    private UUID underwritingCaseId;

    @Column(name = "cycle_number", nullable = false)
    private Integer cycleNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UnderwritingAttemptStatus status;

    @Column(name = "active", nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_details", columnDefinition = "jsonb")
    private Map<String, Object> decisionDetails;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
