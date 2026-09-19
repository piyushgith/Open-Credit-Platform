package com.opencredit.platform.decision;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.decision.dto.CreditDecisionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loans/{reference}/financial-statements/{statementId}/analysis-runs/{analysisRunId}/score/{scoreId}/decision")
@Tag(name = "Credit Decisioning", description = "Decide a score against the active credit policy")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping
    @Operation(summary = "Decide a score against the active credit policy")
    public ResponseEntity<ApiResponse<CreditDecisionResponse>> decide(@PathVariable String reference,
                                                                        @PathVariable UUID statementId,
                                                                        @PathVariable UUID analysisRunId,
                                                                        @PathVariable UUID scoreId) {
        CreditDecisionResponse response = decisionService.decide(reference, statementId, analysisRunId, scoreId);
        return ResponseEntity.ok(ApiResponse.success("Credit decision computed", response));
    }

    @GetMapping
    @Operation(summary = "List every credit decision computed for a score")
    public ResponseEntity<ApiResponse<List<CreditDecisionResponse>>> list(@PathVariable String reference,
                                                                            @PathVariable UUID statementId,
                                                                            @PathVariable UUID analysisRunId,
                                                                            @PathVariable UUID scoreId) {
        return ResponseEntity.ok(ApiResponse.success(decisionService.list(reference, statementId, analysisRunId, scoreId)));
    }
}
