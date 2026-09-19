package com.opencredit.platform.authority;

import com.opencredit.platform.authority.dto.AuthorityMatrixEntryRequest;
import com.opencredit.platform.authority.dto.AuthorityMatrixEntryResponse;
import com.opencredit.platform.authority.exception.AuthorityMatrixEntryNotFoundException;
import com.opencredit.platform.authority.exception.DuplicateAuthorityMatrixMatchOrderException;
import com.opencredit.platform.authority.model.AuthorityMatrixEntry;
import com.opencredit.platform.authority.repository.AuthorityMatrixEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for {@link AuthorityMatrixEntry} configuration. Kept separate from {@link ApprovalService},
 * which only resolves/consumes entries when opening a case — same split
 * {@code CreditPolicyAdminService}/{@code DecisionService} already use.
 */
@Service
@Transactional
public class AuthorityMatrixAdminService {

    private final AuthorityMatrixEntryRepository entryRepository;

    public AuthorityMatrixAdminService(AuthorityMatrixEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    public AuthorityMatrixEntryResponse create(AuthorityMatrixEntryRequest request) {
        assertNoActiveMatchOrderConflict(request.getMatchOrder(), request.getActive(), null);

        AuthorityMatrixEntry entry = AuthorityMatrixEntry.builder()
                .id(UUID.randomUUID())
                .productType(request.getProductType())
                .riskGrade(request.getRiskGrade())
                .minAmount(request.getMinAmount())
                .maxAmount(request.getMaxAmount())
                .requiredLevel(request.getRequiredLevel())
                .matchOrder(request.getMatchOrder())
                .active(request.getActive())
                .createdAt(Instant.now())
                .build();
        saveGuardingMatchOrder(entry, request.getMatchOrder());
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<AuthorityMatrixEntryResponse> list() {
        return entryRepository.findAllByOrderByMatchOrderAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AuthorityMatrixEntryResponse get(UUID entryId) {
        return toResponse(getEntity(entryId));
    }

    public AuthorityMatrixEntryResponse update(UUID entryId, AuthorityMatrixEntryRequest request) {
        AuthorityMatrixEntry entry = getEntity(entryId);
        assertNoActiveMatchOrderConflict(request.getMatchOrder(), request.getActive(), entryId);

        entry.setProductType(request.getProductType());
        entry.setRiskGrade(request.getRiskGrade());
        entry.setMinAmount(request.getMinAmount());
        entry.setMaxAmount(request.getMaxAmount());
        entry.setRequiredLevel(request.getRequiredLevel());
        entry.setMatchOrder(request.getMatchOrder());
        entry.setActive(request.getActive());
        saveGuardingMatchOrder(entry, request.getMatchOrder());
        return toResponse(entry);
    }

    public void delete(UUID entryId) {
        entryRepository.delete(getEntity(entryId));
    }

    /**
     * Fast-path guard against two active entries sharing a {@code matchOrder}, which would make
     * {@code AuthorityMatrixResolver}'s ordering non-deterministic — see
     * {@code uq_authority_matrix_entry_active_match_order}, the real guard this backs up.
     */
    private void assertNoActiveMatchOrderConflict(int matchOrder, boolean active, UUID excludingEntryId) {
        if (!active) {
            return;
        }
        boolean conflict = excludingEntryId == null
                ? entryRepository.existsByActiveTrueAndMatchOrder(matchOrder)
                : entryRepository.existsByActiveTrueAndMatchOrderAndIdNot(matchOrder, excludingEntryId);
        if (conflict) {
            throw new DuplicateAuthorityMatrixMatchOrderException(matchOrder);
        }
    }

    private void saveGuardingMatchOrder(AuthorityMatrixEntry entry, int matchOrder) {
        try {
            entryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAuthorityMatrixMatchOrderException(matchOrder);
        }
    }

    private AuthorityMatrixEntry getEntity(UUID entryId) {
        return entryRepository.findById(entryId)
                .orElseThrow(() -> new AuthorityMatrixEntryNotFoundException(entryId));
    }

    private AuthorityMatrixEntryResponse toResponse(AuthorityMatrixEntry entry) {
        return AuthorityMatrixEntryResponse.builder()
                .id(entry.getId())
                .productType(entry.getProductType())
                .riskGrade(entry.getRiskGrade())
                .minAmount(entry.getMinAmount())
                .maxAmount(entry.getMaxAmount())
                .requiredLevel(entry.getRequiredLevel())
                .matchOrder(entry.getMatchOrder())
                .active(entry.isActive())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
