package com.opencredit.platform.loan;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.model.LoanApplication;
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
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

/**
 * Single entry point for all loan products. Deliberately thin: routing to the
 * correct business logic happens in {@link LoanServiceContext}, not here.
 */
@RestController
@RequestMapping("/api/loans")
@Tag(name = "Loans", description = "Apply for a loan and look up past decisions, across all products")
public class LoanController {

    private final LoanApplicationService loanApplicationService;
    private final ObjectMapper objectMapper;

    public LoanController(LoanApplicationService loanApplicationService, ObjectMapper objectMapper) {
        this.loanApplicationService = loanApplicationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/apply")
    @Operation(
            summary = "Apply for a loan",
            description = "Accepts any registered product's request payload, keyed by the `productType` "
                    + "discriminator (currently PERSONAL or VEHICLE), and returns that product's decision."
    )
    public ResponseEntity<ApiResponse<LoanResponse>> apply(@Valid @RequestBody LoanRequest request) {
        LoanResponse response = loanApplicationService.apply(request);
        return ResponseEntity
                .created(URI.create("/api/loans/" + response.getApplicationReference()))
                .body(ApiResponse.success("Loan application processed successfully", response));
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Look up a previously processed loan application by its reference number")
    public ResponseEntity<ApiResponse<LoanResponse>> getByReference(
            @Parameter(description = "Reference number returned by POST /apply, e.g. LN-00000001")
            @PathVariable String reference) {
        LoanApplication application = loanApplicationService.findByReference(reference);
        LoanResponse response = objectMapper.convertValue(application.getDecisionDetails(), LoanResponse.class);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
