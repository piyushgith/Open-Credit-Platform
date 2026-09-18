package com.opencredit.platform.underwriting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One underwriting case per {@code loan_application} (1:1, {@code application_id UNIQUE}),
 * grouping every {@link UnderwritingAttempt} ever run for it.
 */
@Entity
@Table(name = "underwriting_case")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingCase {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
