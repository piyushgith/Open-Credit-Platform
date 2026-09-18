package com.opencredit.platform.financial;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.financial.dto.FinancialAnalysisRunResponse;
import com.opencredit.platform.financial.dto.FinancialStatementRequest;
import com.opencredit.platform.financial.dto.FinancialStatementResponse;
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
@RequestMapping("/api/loans/{reference}/financial-statements")
@Tag(name = "Financial Statements", description = "Submit financial statements and run the financial analysis engine")
public class FinancialStatementController {

    private final FinancialStatementService financialStatementService;

    public FinancialStatementController(FinancialStatementService financialStatementService) {
        this.financialStatementService = financialStatementService;
    }

    @PostMapping
    @Operation(summary = "Submit a financial statement for a loan application")
    public ResponseEntity<ApiResponse<FinancialStatementResponse>> submit(@PathVariable String reference,
                                                                            @Valid @RequestBody FinancialStatementRequest request) {
        FinancialStatementResponse response = financialStatementService.submit(reference, request);
        return ResponseEntity.ok(ApiResponse.success("Financial statement submitted", response));
    }

    @GetMapping
    @Operation(summary = "List all financial statements submitted for a loan application")
    public ResponseEntity<ApiResponse<List<FinancialStatementResponse>>> list(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(financialStatementService.list(reference)));
    }

    @GetMapping("/{statementId}")
    @Operation(summary = "Get one financial statement, including its raw line items")
    public ResponseEntity<ApiResponse<FinancialStatementResponse>> get(@PathVariable String reference,
                                                                          @PathVariable UUID statementId) {
        return ResponseEntity.ok(ApiResponse.success(financialStatementService.get(reference, statementId)));
    }

    @PostMapping("/{statementId}/analyze")
    @Operation(summary = "Run the financial analysis engine against a statement's line items")
    public ResponseEntity<ApiResponse<FinancialAnalysisRunResponse>> analyze(@PathVariable String reference,
                                                                                @PathVariable UUID statementId) {
        FinancialAnalysisRunResponse response = financialStatementService.analyze(reference, statementId);
        return ResponseEntity.ok(ApiResponse.success("Financial analysis completed", response));
    }

    @GetMapping("/{statementId}/analysis")
    @Operation(summary = "Get the full analysis run history for a financial statement")
    public ResponseEntity<ApiResponse<List<FinancialAnalysisRunResponse>>> getAnalysisRuns(@PathVariable String reference,
                                                                                              @PathVariable UUID statementId) {
        return ResponseEntity.ok(ApiResponse.success(financialStatementService.getAnalysisRuns(reference, statementId)));
    }
}
