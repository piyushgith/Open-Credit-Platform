package com.opencredit.platform.decision;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.decision.dto.CreditPolicyRequest;
import com.opencredit.platform.decision.dto.CreditPolicyResponse;
import com.opencredit.platform.decision.dto.CreditRuleRequest;
import com.opencredit.platform.decision.dto.CreditRuleResponse;
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
@RequestMapping("/api/credit-policies")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Credit Policy Administration", description = "Create, configure and activate credit decision policies")
public class CreditPolicyAdminController {

    private final CreditPolicyAdminService creditPolicyAdminService;

    public CreditPolicyAdminController(CreditPolicyAdminService creditPolicyAdminService) {
        this.creditPolicyAdminService = creditPolicyAdminService;
    }

    @PostMapping
    @Operation(summary = "Create a new credit policy (inactive by default)")
    public ResponseEntity<ApiResponse<CreditPolicyResponse>> create(@Valid @RequestBody CreditPolicyRequest request) {
        CreditPolicyResponse response = creditPolicyAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/credit-policies/" + response.getId()))
                .body(ApiResponse.success("Credit policy created", response));
    }

    @GetMapping
    @Operation(summary = "List all credit policies")
    public ResponseEntity<ApiResponse<List<CreditPolicyResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(creditPolicyAdminService.list()));
    }

    @GetMapping("/{policyId}")
    @Operation(summary = "Get one credit policy, including its rules")
    public ResponseEntity<ApiResponse<CreditPolicyResponse>> get(@PathVariable UUID policyId) {
        return ResponseEntity.ok(ApiResponse.success(creditPolicyAdminService.get(policyId)));
    }

    @PutMapping("/{policyId}")
    @Operation(summary = "Update a credit policy's name")
    public ResponseEntity<ApiResponse<CreditPolicyResponse>> update(@PathVariable UUID policyId,
                                                                       @Valid @RequestBody CreditPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(creditPolicyAdminService.update(policyId, request)));
    }

    @DeleteMapping("/{policyId}")
    @Operation(summary = "Delete a credit policy (must be inactive and never used to decide a score)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID policyId) {
        creditPolicyAdminService.delete(policyId);
        return ResponseEntity.ok(ApiResponse.success("Credit policy deleted", null));
    }

    @PostMapping("/{policyId}/activate")
    @Operation(summary = "Activate a credit policy, deactivating the previously-active one")
    public ResponseEntity<ApiResponse<CreditPolicyResponse>> activate(@PathVariable UUID policyId) {
        return ResponseEntity.ok(ApiResponse.success("Credit policy activated", creditPolicyAdminService.activate(policyId)));
    }

    @PostMapping("/{policyId}/rules")
    @Operation(summary = "Add a rule to a credit policy")
    public ResponseEntity<ApiResponse<CreditRuleResponse>> addRule(@PathVariable UUID policyId,
                                                                      @Valid @RequestBody CreditRuleRequest request) {
        CreditRuleResponse response = creditPolicyAdminService.addRule(policyId, request);
        return ResponseEntity.created(URI.create("/api/credit-policies/" + policyId + "/rules/" + response.getId()))
                .body(ApiResponse.success("Credit rule added", response));
    }

    @PutMapping("/{policyId}/rules/{ruleId}")
    @Operation(summary = "Update a credit policy's rule")
    public ResponseEntity<ApiResponse<CreditRuleResponse>> updateRule(@PathVariable UUID policyId,
                                                                         @PathVariable UUID ruleId,
                                                                         @Valid @RequestBody CreditRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(creditPolicyAdminService.updateRule(policyId, ruleId, request)));
    }

    @DeleteMapping("/{policyId}/rules/{ruleId}")
    @Operation(summary = "Delete a credit policy's rule")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID policyId, @PathVariable UUID ruleId) {
        creditPolicyAdminService.deleteRule(policyId, ruleId);
        return ResponseEntity.ok(ApiResponse.success("Credit rule deleted", null));
    }
}
