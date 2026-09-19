package com.opencredit.platform.authority;

import com.opencredit.platform.authority.dto.ApprovalCaseResponse;
import com.opencredit.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/credit-decisions/{decisionId}/approval-case")
@Tag(name = "Approval Cases", description = "Open and inspect the maker-checker case for a credit decision")
public class CreditDecisionApprovalController {

    private final ApprovalService approvalService;

    public CreditDecisionApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping
    @Operation(summary = "Open an approval case for a credit decision, resolving the required authority level")
    public ResponseEntity<ApiResponse<ApprovalCaseResponse>> open(@PathVariable UUID decisionId) {
        ApprovalCaseResponse response = approvalService.openCase(decisionId);
        return ResponseEntity.created(URI.create("/api/approval-cases/" + response.getId()))
                .body(ApiResponse.success("Approval case opened", response));
    }

    @GetMapping
    @Operation(summary = "Get the approval case opened for a credit decision")
    public ResponseEntity<ApiResponse<ApprovalCaseResponse>> get(@PathVariable UUID decisionId) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getCaseByDecision(decisionId)));
    }
}
