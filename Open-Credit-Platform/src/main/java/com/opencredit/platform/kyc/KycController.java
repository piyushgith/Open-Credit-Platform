package com.opencredit.platform.kyc;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.kyc.dto.KycCaseResponse;
import com.opencredit.platform.kyc.dto.KycRejectRequest;
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

@RestController
@RequestMapping("/api/loans/{reference}/kyc")
@Tag(name = "KYC", description = "Initiate and resolve KYC for a loan application")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping
    @Operation(summary = "Initiate a KYC case for a loan application")
    public ResponseEntity<ApiResponse<KycCaseResponse>> initiate(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success("KYC case initiated", kycService.initiate(reference)));
    }

    @GetMapping
    @Operation(summary = "Look up the KYC case for a loan application")
    public ResponseEntity<ApiResponse<KycCaseResponse>> get(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(kycService.get(reference)));
    }

    @PostMapping("/verify")
    @Operation(summary = "Mark a pending KYC case as verified")
    public ResponseEntity<ApiResponse<KycCaseResponse>> verify(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(kycService.verify(reference)));
    }

    @PostMapping("/reject")
    @Operation(summary = "Mark a pending KYC case as rejected")
    public ResponseEntity<ApiResponse<KycCaseResponse>> reject(@PathVariable String reference,
                                                                 @Valid @RequestBody(required = false) KycRejectRequest request) {
        String remarks = request == null ? null : request.getRemarks();
        return ResponseEntity.ok(ApiResponse.success(kycService.reject(reference, remarks)));
    }
}
