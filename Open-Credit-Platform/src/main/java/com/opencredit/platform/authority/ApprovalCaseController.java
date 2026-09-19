package com.opencredit.platform.authority;

import com.opencredit.platform.authority.dto.ApprovalActionRequest;
import com.opencredit.platform.authority.dto.ApprovalCaseResponse;
import com.opencredit.platform.common.api.ApiResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/approval-cases/{caseId}")
@Tag(name = "Approval Cases", description = "Record maker and checker decisions on an approval case")
public class ApprovalCaseController {

    private final ApprovalService approvalService;

    public ApprovalCaseController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @Operation(summary = "Get an approval case, including every maker/checker decision recorded on it")
    public ResponseEntity<ApiResponse<ApprovalCaseResponse>> get(@PathVariable UUID caseId) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getCase(caseId)));
    }

    @PostMapping("/maker-decision")
    @Operation(summary = "Record the maker's recommendation on an approval case")
    public ResponseEntity<ApiResponse<ApprovalCaseResponse>> makerDecision(@PathVariable UUID caseId,
                                                                              @Valid @RequestBody ApprovalActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Maker decision recorded", approvalService.recordMakerDecision(caseId, request)));
    }

    @PostMapping("/checker-decision")
    @Operation(summary = "Record the checker's final decision on an approval case")
    public ResponseEntity<ApiResponse<ApprovalCaseResponse>> checkerDecision(@PathVariable UUID caseId,
                                                                                @Valid @RequestBody ApprovalActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Checker decision recorded", approvalService.recordCheckerDecision(caseId, request)));
    }
}
