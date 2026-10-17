package com.opencredit.platform.authority;

import com.opencredit.platform.authority.dto.ApprovalCaseSummaryResponse;
import com.opencredit.platform.authority.model.ApprovalCaseStatus;
import com.opencredit.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approval-cases")
@Tag(name = "Approval Cases", description = "The maker-checker approval queue")
public class ApprovalQueueController {

    private final ApprovalService approvalService;

    public ApprovalQueueController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @Operation(summary = "List approval cases oldest first, optionally filtered by status",
            description = "Each row carries the application it belongs to. Paged with `page` (0-based) and "
                    + "`size` (max 100). Sort order is fixed. Use status=PENDING_MAKER or PENDING_CHECKER for "
                    + "the work waiting on each role.")
    public ResponseEntity<ApiResponse<PagedModel<ApprovalCaseSummaryResponse>>> list(
            @RequestParam(required = false) ApprovalCaseStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(new PagedModel<>(approvalService.listCases(status, pageable))));
    }
}
