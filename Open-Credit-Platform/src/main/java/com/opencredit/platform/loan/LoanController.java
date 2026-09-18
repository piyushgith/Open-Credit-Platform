package com.opencredit.platform.loan;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Single entry point for all loan products. Deliberately thin: routing to the correct
 * business logic happens in {@link LoanServiceContext}, and lifecycle transitions are
 * guarded by {@link com.opencredit.platform.loan.support.ApplicationLifecycle}, not here.
 */
@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans", description = "Apply for a loan and move it through its lifecycle")
public class LoanController {

    private final LoanApplicationService loanApplicationService;

    public LoanController(LoanApplicationService loanApplicationService) {
        this.loanApplicationService = loanApplicationService;
    }

    @PostMapping("/apply")
    @Operation(
            summary = "Apply for a loan",
            description = "Accepts any registered product's request payload, keyed by the `productType` "
                    + "discriminator (currently PERSONAL or VEHICLE), and creates it as a DRAFT application."
    )
    public ResponseEntity<ApiResponse<LoanResponse>> apply(@Valid @RequestBody LoanRequest request) {
        LoanResponse response = loanApplicationService.apply(request);
        return ResponseEntity
                .created(URI.create("/api/loans/" + response.getApplicationReference()))
                .body(ApiResponse.success("Loan application created", response));
    }

    @PostMapping("/{reference}/submit")
    @Operation(summary = "Submit a DRAFT application, running underwriting and landing on OFFERED or DECLINED")
    public ResponseEntity<ApiResponse<LoanResponse>> submit(
            @Parameter(description = "Reference number returned by POST /apply") @PathVariable String reference) {
        LoanResponse response = loanApplicationService.submit(reference);
        return ResponseEntity.ok(ApiResponse.success("Loan application underwritten", response));
    }

    @PostMapping("/{reference}/sanction")
    @Operation(summary = "Sanction an OFFERED application")
    public ResponseEntity<ApiResponse<LoanResponse>> sanction(@PathVariable String reference) {
        LoanResponse response = loanApplicationService.sanction(reference);
        return ResponseEntity.ok(ApiResponse.success("Loan application sanctioned", response));
    }

    @PostMapping("/{reference}/disburse")
    @Operation(summary = "Disburse a SANCTIONED application")
    public ResponseEntity<ApiResponse<LoanResponse>> disburse(@PathVariable String reference) {
        LoanResponse response = loanApplicationService.disburse(reference);
        return ResponseEntity.ok(ApiResponse.success("Loan disbursed", response));
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Look up a loan application by its reference number, at any lifecycle stage")
    public ResponseEntity<ApiResponse<LoanResponse>> getByReference(
            @Parameter(description = "Reference number returned by POST /apply, e.g. LN-00000001")
            @PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(loanApplicationService.getResponse(reference)));
    }
}
