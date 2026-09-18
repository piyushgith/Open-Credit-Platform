package com.opencredit.platform.document.model;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a document attached to a {@code loan_application}. No blob storage exists yet,
 * so {@code storageReference} is a caller-supplied pointer (e.g. a URL or path), not a file upload.
 */
@Entity
@Table(name = "loan_document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDocument {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_reference", nullable = false)
    private String storageReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
