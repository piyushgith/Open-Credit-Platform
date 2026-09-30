package com.opencredit.platform.document.dto;

import com.opencredit.platform.document.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoanDocumentRequest {

    @NotNull
    private DocumentType documentType;

    @NotBlank
    @Size(max = 255)
    private String fileName;

    @NotBlank
    @Size(max = 500)
    private String storageReference;
}
