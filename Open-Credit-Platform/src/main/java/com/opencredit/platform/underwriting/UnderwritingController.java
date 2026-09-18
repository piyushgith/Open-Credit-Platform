package com.opencredit.platform.underwriting;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.underwriting.dto.UnderwritingCaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans/{reference}/underwriting")
@Tag(name = "Underwriting", description = "Read the underwriting case and attempt history for a loan application")
public class UnderwritingController {

    private final UnderwritingService underwritingService;

    public UnderwritingController(UnderwritingService underwritingService) {
        this.underwritingService = underwritingService;
    }

    @GetMapping
    @Operation(summary = "Get the underwriting case and full attempt history for a loan application")
    public ResponseEntity<ApiResponse<UnderwritingCaseResponse>> get(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(underwritingService.getCaseByReference(reference)));
    }
}
