package com.opencredit.platform.document;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.document.dto.LoanDocumentRequest;
import com.opencredit.platform.document.dto.LoanDocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loans/{reference}/documents")
@Tag(name = "Loan Documents", description = "Attach and verify documents for a loan application")
public class LoanDocumentController {

    private final LoanDocumentService loanDocumentService;

    public LoanDocumentController(LoanDocumentService loanDocumentService) {
        this.loanDocumentService = loanDocumentService;
    }

    @PostMapping
    @Operation(summary = "Attach a document to a loan application")
    public ResponseEntity<ApiResponse<LoanDocumentResponse>> upload(@PathVariable String reference,
                                                                      @Valid @RequestBody LoanDocumentRequest request) {
        LoanDocumentResponse response = loanDocumentService.upload(reference, request);
        return ResponseEntity.ok(ApiResponse.success("Document attached", response));
    }

    @GetMapping
    @Operation(summary = "List all documents attached to a loan application")
    public ResponseEntity<ApiResponse<List<LoanDocumentResponse>>> list(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(loanDocumentService.list(reference)));
    }

    @PostMapping("/{documentId}/verify")
    @Operation(summary = "Mark an uploaded document as verified")
    public ResponseEntity<ApiResponse<LoanDocumentResponse>> verify(@PathVariable String reference,
                                                                      @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(loanDocumentService.verify(reference, documentId)));
    }

    @PostMapping("/{documentId}/reject")
    @Operation(summary = "Mark an uploaded document as rejected")
    public ResponseEntity<ApiResponse<LoanDocumentResponse>> reject(@PathVariable String reference,
                                                                      @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success(loanDocumentService.reject(reference, documentId)));
    }
}
