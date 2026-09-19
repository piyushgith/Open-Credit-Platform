package com.opencredit.platform.authority;

import com.opencredit.platform.common.api.ApiResponse;
import com.opencredit.platform.authority.dto.AuthorityMatrixEntryRequest;
import com.opencredit.platform.authority.dto.AuthorityMatrixEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/authority-matrix")
@Tag(name = "Authority Matrix Administration", description = "Configure which approval level a decision's amount/risk-grade/product requires")
public class AuthorityMatrixAdminController {

    private final AuthorityMatrixAdminService authorityMatrixAdminService;

    public AuthorityMatrixAdminController(AuthorityMatrixAdminService authorityMatrixAdminService) {
        this.authorityMatrixAdminService = authorityMatrixAdminService;
    }

    @PostMapping
    @Operation(summary = "Create an authority matrix entry")
    public ResponseEntity<ApiResponse<AuthorityMatrixEntryResponse>> create(
            @Valid @RequestBody AuthorityMatrixEntryRequest request) {
        AuthorityMatrixEntryResponse response = authorityMatrixAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/authority-matrix/" + response.getId()))
                .body(ApiResponse.success("Authority matrix entry created", response));
    }

    @GetMapping
    @Operation(summary = "List all authority matrix entries, ordered by match priority")
    public ResponseEntity<ApiResponse<List<AuthorityMatrixEntryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(authorityMatrixAdminService.list()));
    }

    @GetMapping("/{entryId}")
    @Operation(summary = "Get one authority matrix entry")
    public ResponseEntity<ApiResponse<AuthorityMatrixEntryResponse>> get(@PathVariable UUID entryId) {
        return ResponseEntity.ok(ApiResponse.success(authorityMatrixAdminService.get(entryId)));
    }

    @PutMapping("/{entryId}")
    @Operation(summary = "Update an authority matrix entry")
    public ResponseEntity<ApiResponse<AuthorityMatrixEntryResponse>> update(
            @PathVariable UUID entryId, @Valid @RequestBody AuthorityMatrixEntryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authorityMatrixAdminService.update(entryId, request)));
    }

    @DeleteMapping("/{entryId}")
    @Operation(summary = "Delete an authority matrix entry")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID entryId) {
        authorityMatrixAdminService.delete(entryId);
        return ResponseEntity.ok(ApiResponse.success("Authority matrix entry deleted", null));
    }
}
