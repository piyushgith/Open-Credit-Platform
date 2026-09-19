package com.opencredit.platform.offer;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.offer.dto.OfferRequest;
import com.opencredit.platform.offer.dto.OfferResponse;
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

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loans/{reference}/offers")
@Tag(name = "Offers", description = "Create, list and select offers for a loan application with a successful underwriting decision")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping
    @Operation(summary = "Create an offer from the application's approved underwriting decision")
    public ResponseEntity<ApiResponse<OfferResponse>> create(@PathVariable String reference,
                                                              @Valid @RequestBody OfferRequest request) {
        OfferResponse response = offerService.createOffer(reference, request);
        return ResponseEntity.created(URI.create("/api/loans/" + reference + "/offers/" + response.getId()))
                .body(ApiResponse.success("Offer created", response));
    }

    @GetMapping
    @Operation(summary = "List every offer created for a loan application")
    public ResponseEntity<ApiResponse<List<OfferResponse>>> list(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.success(offerService.listOffers(reference)));
    }

    @PostMapping("/{offerId}/select")
    @Operation(summary = "Select an offer, making it eligible for sanction")
    public ResponseEntity<ApiResponse<OfferResponse>> select(@PathVariable String reference,
                                                              @PathVariable UUID offerId) {
        return ResponseEntity.ok(ApiResponse.success("Offer selected", offerService.selectOffer(reference, offerId)));
    }
}
