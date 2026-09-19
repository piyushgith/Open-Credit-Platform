package com.opencredit.platform.offer;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.offer.dto.SanctionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans/{reference}/sanction")
@Tag(name = "Sanction", description = "Read the sanction issued for a loan application")
public class SanctionController {

    private final OfferService offerService;

    public SanctionController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping
    @Operation(summary = "Get the sanction issued for a loan application")
    public ResponseEntity<ApiResponse<SanctionResponse>> get(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(offerService.getSanction(reference)));
    }
}
