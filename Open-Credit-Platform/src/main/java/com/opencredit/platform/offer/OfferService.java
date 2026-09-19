package com.opencredit.platform.offer;

import com.opencredit.platform.audit.AuditService;
import com.opencredit.platform.audit.model.AuditEventType;
import com.opencredit.platform.loan.LoanProductService;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.exception.LoanProductConstraintViolationException;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.model.LoanProduct;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.loan.support.EmiCalculator;
import com.opencredit.platform.offer.dto.OfferRequest;
import com.opencredit.platform.offer.dto.OfferResponse;
import com.opencredit.platform.offer.dto.SanctionResponse;
import com.opencredit.platform.offer.exception.NoOfferSelectedException;
import com.opencredit.platform.offer.exception.OfferAlreadySelectedException;
import com.opencredit.platform.offer.exception.OfferNotEligibleException;
import com.opencredit.platform.offer.exception.OfferNotEligibleForSelectionException;
import com.opencredit.platform.offer.exception.OfferNotFoundException;
import com.opencredit.platform.offer.exception.DuplicateSanctionException;
import com.opencredit.platform.offer.exception.SanctionNotFoundException;
import com.opencredit.platform.offer.model.Offer;
import com.opencredit.platform.offer.model.OfferStatus;
import com.opencredit.platform.offer.model.Sanction;
import com.opencredit.platform.offer.repository.OfferRepository;
import com.opencredit.platform.offer.repository.SanctionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creates, lists and selects {@link Offer}s, and creates the {@link Sanction} for the selected
 * offer. Reads {@code loan}'s {@code LoanApplication} directly (read-only, for eligibility checks
 * and to read the underwriting decision's approved amount/rate) — the same cross-module-repository
 * pattern {@code DecisionService}/{@code ApprovalService} already use. Never mutates
 * {@code LoanApplication.status}; only {@code LoanApplicationService} does that, calling
 * {@link #sanctionSelectedOffer(UUID)} to get the domain result first.
 */
@Service
@Transactional
public class OfferService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductService loanProductService;
    private final EmiCalculator emiCalculator;
    private final OfferRepository offerRepository;
    private final SanctionRepository sanctionRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public OfferService(LoanApplicationRepository loanApplicationRepository,
                         LoanProductService loanProductService,
                         EmiCalculator emiCalculator,
                         OfferRepository offerRepository,
                         SanctionRepository sanctionRepository,
                         ObjectMapper objectMapper,
                         AuditService auditService) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.loanProductService = loanProductService;
        this.emiCalculator = emiCalculator;
        this.offerRepository = offerRepository;
        this.sanctionRepository = sanctionRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public OfferResponse createOffer(String referenceNumber, OfferRequest request) {
        LoanApplication application = getApplication(referenceNumber);
        if (application.getStatus() != ApplicationStatus.OFFERED
                || application.getDecision() != DecisionStatus.APPROVED) {
            throw new OfferNotEligibleException(referenceNumber, application.getStatus());
        }

        LoanResponse decision = objectMapper.convertValue(application.getDecisionDetails(), LoanResponse.class);
        BigDecimal approvedAmount = decision.getApprovedAmount();
        BigDecimal offerAmount = request.getRequestedAmount() != null ? request.getRequestedAmount() : approvedAmount;
        if (offerAmount.compareTo(approvedAmount) > 0) {
            throw new LoanProductConstraintViolationException(
                    "requestedAmount " + offerAmount + " cannot exceed the approved amount " + approvedAmount);
        }

        LoanProduct product = loanProductService.getActiveEntity(application.getProductType());
        if (request.getTenureMonths() < product.getMinTenureMonths()
                || request.getTenureMonths() > product.getMaxTenureMonths()) {
            throw new LoanProductConstraintViolationException(
                    "tenureMonths must be between " + product.getMinTenureMonths() + " and "
                            + product.getMaxTenureMonths() + " for product " + product.getProductType());
        }

        BigDecimal monthlyEmi = emiCalculator.calculate(offerAmount, decision.getInterestRate(), request.getTenureMonths());

        Offer offer = offerRepository.save(Offer.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getId())
                .offerAmount(offerAmount)
                .interestRate(decision.getInterestRate())
                .tenureMonths(request.getTenureMonths())
                .monthlyEmi(monthlyEmi)
                .status(OfferStatus.ACTIVE)
                .createdAt(Instant.now())
                .build());

        auditService.recordEvent(AuditEventType.OFFER_CREATED, "Offer", offer.getId(),
                "Offer of " + offerAmount + " over " + request.getTenureMonths() + " months for " + referenceNumber);

        return toResponse(offer, referenceNumber);
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> listOffers(String referenceNumber) {
        LoanApplication application = getApplication(referenceNumber);
        return offerRepository.findAllByApplicationIdOrderByCreatedAtAsc(application.getId()).stream()
                .map(offer -> toResponse(offer, referenceNumber))
                .toList();
    }

    public OfferResponse selectOffer(String referenceNumber, UUID offerId) {
        LoanApplication application = getApplication(referenceNumber);
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new OfferNotFoundException(offerId));
        if (!offer.getApplicationId().equals(application.getId())) {
            throw new OfferNotFoundException(offerId);
        }
        if (application.getStatus() != ApplicationStatus.OFFERED) {
            throw new OfferNotEligibleForSelectionException(offerId, "application is not OFFERED");
        }
        if (offer.getStatus() != OfferStatus.ACTIVE) {
            throw new OfferNotEligibleForSelectionException(offerId, "offer is not ACTIVE");
        }
        if (offerRepository.existsByApplicationIdAndStatus(application.getId(), OfferStatus.SELECTED)) {
            throw new OfferAlreadySelectedException(referenceNumber);
        }

        OfferStatus previousStatus = offer.getStatus();
        offer.setStatus(OfferStatus.SELECTED);
        try {
            offerRepository.saveAndFlush(offer);
        } catch (DataIntegrityViolationException ex) {
            throw new OfferAlreadySelectedException(referenceNumber);
        }
        auditService.recordDataChange("Offer", offer.getId(), "status", previousStatus, offer.getStatus());
        auditService.recordEvent(AuditEventType.OFFER_ACCEPTED, "Offer", offer.getId(),
                "Offer selected for " + referenceNumber);

        return toResponse(offer, referenceNumber);
    }

    @Transactional(readOnly = true)
    public SanctionResponse getSanction(String referenceNumber) {
        LoanApplication application = getApplication(referenceNumber);
        Sanction sanction = sanctionRepository.findByApplicationId(application.getId())
                .orElseThrow(() -> new SanctionNotFoundException(referenceNumber));
        return toResponse(sanction, referenceNumber);
    }

    /**
     * Called by {@code LoanApplicationService.sanction} once it has confirmed the
     * {@code OFFERED -> SANCTIONED} transition is legal. Requires a {@link OfferStatus#SELECTED}
     * offer and snapshots its terms into a new {@link Sanction}.
     */
    public Sanction sanctionSelectedOffer(UUID applicationId) {
        if (sanctionRepository.existsByApplicationId(applicationId)) {
            throw new DuplicateSanctionException(applicationId);
        }
        Offer selected = offerRepository.findByApplicationIdAndStatus(applicationId, OfferStatus.SELECTED)
                .orElseThrow(() -> new NoOfferSelectedException(applicationId));

        Sanction sanction = sanctionRepository.save(Sanction.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .offerId(selected.getId())
                .sanctionedAmount(selected.getOfferAmount())
                .interestRate(selected.getInterestRate())
                .tenureMonths(selected.getTenureMonths())
                .monthlyEmi(selected.getMonthlyEmi())
                .createdAt(Instant.now())
                .build());

        auditService.recordEvent(AuditEventType.SANCTION_APPROVED, "Sanction", sanction.getId(),
                "Sanctioned " + sanction.getSanctionedAmount() + " for application " + applicationId);

        return sanction;
    }

    private LoanApplication getApplication(String referenceNumber) {
        return loanApplicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
    }

    private OfferResponse toResponse(Offer offer, String referenceNumber) {
        return OfferResponse.builder()
                .id(offer.getId())
                .applicationReference(referenceNumber)
                .offerAmount(offer.getOfferAmount())
                .interestRate(offer.getInterestRate())
                .tenureMonths(offer.getTenureMonths())
                .monthlyEmi(offer.getMonthlyEmi())
                .status(offer.getStatus())
                .createdAt(offer.getCreatedAt())
                .build();
    }

    private SanctionResponse toResponse(Sanction sanction, String referenceNumber) {
        return SanctionResponse.builder()
                .id(sanction.getId())
                .applicationReference(referenceNumber)
                .offerId(sanction.getOfferId())
                .sanctionedAmount(sanction.getSanctionedAmount())
                .interestRate(sanction.getInterestRate())
                .tenureMonths(sanction.getTenureMonths())
                .monthlyEmi(sanction.getMonthlyEmi())
                .createdAt(sanction.getCreatedAt())
                .build();
    }
}
