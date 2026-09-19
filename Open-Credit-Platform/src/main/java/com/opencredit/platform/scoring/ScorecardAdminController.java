package com.opencredit.platform.scoring;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.scoring.dto.ScorecardRequest;
import com.opencredit.platform.scoring.dto.ScorecardResponse;
import com.opencredit.platform.scoring.dto.ScorecardRuleRequest;
import com.opencredit.platform.scoring.dto.ScorecardRuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scorecards")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Scorecard Administration", description = "Create, configure and activate credit-scoring scorecards")
public class ScorecardAdminController {

    private final ScorecardAdminService scorecardAdminService;

    public ScorecardAdminController(ScorecardAdminService scorecardAdminService) {
        this.scorecardAdminService = scorecardAdminService;
    }

    @PostMapping
    @Operation(summary = "Create a new scorecard (inactive by default)")
    public ResponseEntity<ApiResponse<ScorecardResponse>> create(@Valid @RequestBody ScorecardRequest request) {
        ScorecardResponse response = scorecardAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/scorecards/" + response.getId()))
                .body(ApiResponse.success("Scorecard created", response));
    }

    @GetMapping
    @Operation(summary = "List all scorecards")
    public ResponseEntity<ApiResponse<List<ScorecardResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(scorecardAdminService.list()));
    }

    @GetMapping("/{scorecardId}")
    @Operation(summary = "Get one scorecard, including its rule bands")
    public ResponseEntity<ApiResponse<ScorecardResponse>> get(@PathVariable UUID scorecardId) {
        return ResponseEntity.ok(ApiResponse.success(scorecardAdminService.get(scorecardId)));
    }

    @PutMapping("/{scorecardId}")
    @Operation(summary = "Update a scorecard's name and score envelope")
    public ResponseEntity<ApiResponse<ScorecardResponse>> update(@PathVariable UUID scorecardId,
                                                                    @Valid @RequestBody ScorecardRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scorecardAdminService.update(scorecardId, request)));
    }

    @DeleteMapping("/{scorecardId}")
    @Operation(summary = "Delete a scorecard (must be inactive and never used to score a run)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID scorecardId) {
        scorecardAdminService.delete(scorecardId);
        return ResponseEntity.ok(ApiResponse.success("Scorecard deleted", null));
    }

    @PostMapping("/{scorecardId}/activate")
    @Operation(summary = "Activate a scorecard, deactivating the previously-active one")
    public ResponseEntity<ApiResponse<ScorecardResponse>> activate(@PathVariable UUID scorecardId) {
        return ResponseEntity.ok(ApiResponse.success("Scorecard activated", scorecardAdminService.activate(scorecardId)));
    }

    @PostMapping("/{scorecardId}/rules")
    @Operation(summary = "Add a rule band to a scorecard")
    public ResponseEntity<ApiResponse<ScorecardRuleResponse>> addRule(@PathVariable UUID scorecardId,
                                                                         @Valid @RequestBody ScorecardRuleRequest request) {
        ScorecardRuleResponse response = scorecardAdminService.addRule(scorecardId, request);
        return ResponseEntity.created(URI.create("/api/scorecards/" + scorecardId + "/rules/" + response.getId()))
                .body(ApiResponse.success("Scorecard rule added", response));
    }

    @PutMapping("/{scorecardId}/rules/{ruleId}")
    @Operation(summary = "Update a scorecard's rule band")
    public ResponseEntity<ApiResponse<ScorecardRuleResponse>> updateRule(@PathVariable UUID scorecardId,
                                                                            @PathVariable UUID ruleId,
                                                                            @Valid @RequestBody ScorecardRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scorecardAdminService.updateRule(scorecardId, ruleId, request)));
    }

    @DeleteMapping("/{scorecardId}/rules/{ruleId}")
    @Operation(summary = "Delete a scorecard's rule band")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID scorecardId, @PathVariable UUID ruleId) {
        scorecardAdminService.deleteRule(scorecardId, ruleId);
        return ResponseEntity.ok(ApiResponse.success("Scorecard rule deleted", null));
    }
}
