package com.opencredit.platform.scoring;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.scoring.dto.ScoreResponse;
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
@RequestMapping("/api/loans/{reference}/financial-statements/{statementId}/analysis-runs/{analysisRunId}/score")
@Tag(name = "Credit Scoring", description = "Score a financial analysis run against the active scorecard")
public class ScoringController {

    private final ScoreService scoreService;

    public ScoringController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping
    @Operation(summary = "Score a financial analysis run against the active scorecard")
    public ResponseEntity<ApiResponse<ScoreResponse>> score(@PathVariable String reference,
                                                              @PathVariable UUID statementId,
                                                              @PathVariable UUID analysisRunId) {
        ScoreResponse response = scoreService.score(reference, statementId, analysisRunId);
        return ResponseEntity.ok(ApiResponse.success("Score computed", response));
    }

    @GetMapping
    @Operation(summary = "List every score computed for a financial analysis run")
    public ResponseEntity<ApiResponse<List<ScoreResponse>>> list(@PathVariable String reference,
                                                                    @PathVariable UUID statementId,
                                                                    @PathVariable UUID analysisRunId) {
        return ResponseEntity.ok(ApiResponse.success(scoreService.list(reference, statementId, analysisRunId)));
    }
}
