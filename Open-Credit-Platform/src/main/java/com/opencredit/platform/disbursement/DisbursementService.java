package com.opencredit.platform.disbursement;

import com.opencredit.platform.disbursement.dto.DisbursementResponse;
import com.opencredit.platform.disbursement.dto.DisbursementTrancheDetail;
import com.opencredit.platform.disbursement.dto.DisbursementTrancheRequest;
import com.opencredit.platform.disbursement.dto.DisbursementTrancheResponse;
import com.opencredit.platform.disbursement.exception.ConcurrentDisbursementConflictException;
import com.opencredit.platform.disbursement.exception.DisbursementAlreadyCompletedException;
import com.opencredit.platform.disbursement.exception.DisbursementExceedsSanctionedAmountException;
import com.opencredit.platform.disbursement.exception.DisbursementNotFoundException;
import com.opencredit.platform.disbursement.exception.DuplicateDisbursementRequestException;
import com.opencredit.platform.disbursement.model.Disbursement;
import com.opencredit.platform.disbursement.model.DisbursementStatus;
import com.opencredit.platform.disbursement.model.DisbursementTranche;
import com.opencredit.platform.disbursement.repository.DisbursementRepository;
import com.opencredit.platform.disbursement.repository.DisbursementTrancheRepository;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.offer.exception.SanctionNotFoundException;
import com.opencredit.platform.offer.model.Sanction;
import com.opencredit.platform.offer.repository.SanctionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records {@link DisbursementTranche}s against a {@code Sanction}, lazily creating the parent
 * {@link Disbursement} on the first tranche — the same lazy-create-on-first-use pattern
 * {@code UnderwritingService.startAttempt} uses for {@code UnderwritingCase}. Reads {@code offer}'s
 * {@code Sanction} and {@code loan}'s {@code LoanApplication} directly, the established
 * cross-module-repository pattern. Never mutates {@code LoanApplication.status}; returns whether
 * this tranche completed the disbursement so {@code LoanApplicationService} can decide the
 * {@code SANCTIONED -> DISBURSED} transition.
 */
@Service
@Transactional
public class DisbursementService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final SanctionRepository sanctionRepository;
    private final DisbursementRepository disbursementRepository;
    private final DisbursementTrancheRepository trancheRepository;

    public DisbursementService(LoanApplicationRepository loanApplicationRepository,
                                SanctionRepository sanctionRepository,
                                DisbursementRepository disbursementRepository,
                                DisbursementTrancheRepository trancheRepository) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.sanctionRepository = sanctionRepository;
        this.disbursementRepository = disbursementRepository;
        this.trancheRepository = trancheRepository;
    }

    public DisbursementTrancheResponse recordTranche(UUID applicationId, DisbursementTrancheRequest request) {
        Sanction sanction = sanctionRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new SanctionNotFoundException(applicationId));

        Disbursement disbursement = disbursementRepository.findBySanctionId(sanction.getId())
                .orElseGet(() -> disbursementRepository.save(Disbursement.builder()
                        .id(UUID.randomUUID())
                        .sanctionId(sanction.getId())
                        .disbursedTotal(BigDecimal.ZERO)
                        .status(DisbursementStatus.IN_PROGRESS)
                        .createdAt(Instant.now())
                        .build()));

        if (disbursement.getStatus() == DisbursementStatus.COMPLETED) {
            throw new DisbursementAlreadyCompletedException(applicationId);
        }

        BigDecimal newTotal = disbursement.getDisbursedTotal().add(request.getAmount());
        if (newTotal.compareTo(sanction.getSanctionedAmount()) > 0) {
            throw new DisbursementExceedsSanctionedAmountException(
                    request.getAmount(), disbursement.getDisbursedTotal(), sanction.getSanctionedAmount());
        }

        int trancheNumber = (int) trancheRepository.countByDisbursementId(disbursement.getId()) + 1;
        DisbursementTranche tranche = DisbursementTranche.builder()
                .id(UUID.randomUUID())
                .disbursementId(disbursement.getId())
                .trancheNumber(trancheNumber)
                .amount(request.getAmount())
                .requestReference(request.getRequestReference())
                .createdAt(Instant.now())
                .build();
        try {
            trancheRepository.saveAndFlush(tranche);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateDisbursementRequestException(request.getRequestReference());
        }

        boolean fullyDisbursed = newTotal.compareTo(sanction.getSanctionedAmount()) == 0;
        disbursement.setDisbursedTotal(newTotal);
        disbursement.setStatus(fullyDisbursed ? DisbursementStatus.COMPLETED : DisbursementStatus.IN_PROGRESS);
        try {
            disbursementRepository.saveAndFlush(disbursement);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConcurrentDisbursementConflictException(applicationId);
        }

        return DisbursementTrancheResponse.builder()
                .trancheId(tranche.getId())
                .trancheNumber(trancheNumber)
                .amount(tranche.getAmount())
                .requestReference(tranche.getRequestReference())
                .disbursedTotal(newTotal)
                .sanctionedAmount(sanction.getSanctionedAmount())
                .disbursementStatus(disbursement.getStatus())
                .createdAt(tranche.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public DisbursementResponse getDisbursement(String referenceNumber) {
        LoanApplication application = loanApplicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
        Sanction sanction = sanctionRepository.findByApplicationId(application.getId())
                .orElseThrow(() -> new SanctionNotFoundException(referenceNumber));
        Disbursement disbursement = disbursementRepository.findBySanctionId(sanction.getId())
                .orElseThrow(() -> new DisbursementNotFoundException(referenceNumber));

        List<DisbursementTrancheDetail> tranches =
                trancheRepository.findAllByDisbursementIdOrderByTrancheNumberAsc(disbursement.getId()).stream()
                        .map(this::toDetail)
                        .toList();

        return DisbursementResponse.builder()
                .applicationReference(referenceNumber)
                .sanctionedAmount(sanction.getSanctionedAmount())
                .disbursedTotal(disbursement.getDisbursedTotal())
                .status(disbursement.getStatus())
                .tranches(tranches)
                .build();
    }

    private DisbursementTrancheDetail toDetail(DisbursementTranche tranche) {
        return DisbursementTrancheDetail.builder()
                .trancheNumber(tranche.getTrancheNumber())
                .amount(tranche.getAmount())
                .requestReference(tranche.getRequestReference())
                .createdAt(tranche.getCreatedAt())
                .build();
    }
}
