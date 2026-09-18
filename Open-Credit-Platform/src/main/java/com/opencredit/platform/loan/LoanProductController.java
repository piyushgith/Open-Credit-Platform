package com.opencredit.platform.loan;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.loan.dto.LoanProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loan-products")
@Tag(name = "Loan Products", description = "Browse the active loan product catalog")
public class LoanProductController {

    private final LoanProductService loanProductService;

    public LoanProductController(LoanProductService loanProductService) {
        this.loanProductService = loanProductService;
    }

    @GetMapping
    @Operation(summary = "List all active loan products")
    public ResponseEntity<ApiResponse<List<LoanProductResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(loanProductService.findAllActive()));
    }

    @GetMapping("/{productType}")
    @Operation(summary = "Look up the active loan product for a product type")
    public ResponseEntity<ApiResponse<LoanProductResponse>> getByProductType(@PathVariable ProductType productType) {
        return ResponseEntity.ok(ApiResponse.success(loanProductService.findByProductType(productType)));
    }
}
