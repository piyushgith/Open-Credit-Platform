package com.opencredit.platform.document.dto;

import com.opencredit.platform.document.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoanDocumentRequest {

    @NotNull
    private DocumentType documentType;

    @NotBlank
    private String fileName;

    @NotBlank
    private String storageReference;
}
