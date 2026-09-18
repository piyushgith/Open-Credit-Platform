package com.opencredit.platform.document.dto;

import com.opencredit.platform.document.model.DocumentStatus;
import com.opencredit.platform.document.model.DocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LoanDocumentResponse {

    private UUID id;
    private UUID applicationId;
    private DocumentType documentType;
    private String fileName;
    private String storageReference;
    private DocumentStatus status;
    private Instant uploadedAt;
}
