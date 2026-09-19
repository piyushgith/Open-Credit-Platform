package com.opencredit.platform.disbursement;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.disbursement.dto.DisbursementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans/{reference}/disbursements")
@Tag(name = "Disbursements", description = "Read the disbursement and tranche history for a loan application")
public class DisbursementController {

    private final DisbursementService disbursementService;

    public DisbursementController(DisbursementService disbursementService) {
        this.disbursementService = disbursementService;
    }

    @GetMapping
    @Operation(summary = "Get the disbursement running total and full tranche history for a loan application")
    public ResponseEntity<ApiResponse<DisbursementResponse>> get(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(disbursementService.getDisbursement(reference)));
    }
}
