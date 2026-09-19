package com.opencredit.platform.loan;

import com.opencredit.platform.customer.exception.CustomerNotFoundException;
import com.opencredit.platform.customer.model.Customer;
import com.opencredit.platform.customer.repository.CustomerRepository;
import com.opencredit.platform.disbursement.DisbursementService;
import com.opencredit.platform.disbursement.dto.DisbursementTrancheRequest;
import com.opencredit.platform.disbursement.dto.DisbursementTrancheResponse;
import com.opencredit.platform.disbursement.model.DisbursementStatus;
import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.dto.PersonalLoanResponse;
import com.opencredit.platform.loan.dto.VehicleLoanResponse;
import com.opencredit.platform.loan.exception.IllegalApplicationTransitionException;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.exception.LoanProductConstraintViolationException;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.model.LoanProduct;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.loan.strategy.LoanProcessingStrategy;
import com.opencredit.platform.loan.support.ApplicationLifecycle;
import com.opencredit.platform.offer.OfferService;
import com.opencredit.platform.underwriting.UnderwritingService;
import com.opencredit.platform.underwriting.exception.UnderwritingNotRetryableException;
import com.opencredit.platform.underwriting.model.UnderwritingAttempt;
import com.opencredit.platform.underwriting.model.UnderwritingAttemptStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class LoanApplicationService {

    private static final String REFERENCE_PREFIX = "LN-";
    private static final int REFERENCE_DIGITS = 8;

    private final LoanServiceContext loanServiceContext;
    private final LoanApplicationRepository repository;
    private final CustomerRepository customerRepository;
    private final LoanProductService loanProductService;
    private final UnderwritingService underwritingService;
    private final OfferService offerService;
    private final DisbursementService disbursementService;
    private final ObjectMapper objectMapper;

    public LoanApplicationService(LoanServiceContext loanServiceContext,
                                   LoanApplicationRepository repository,
                                   CustomerRepository customerRepository,
                                   LoanProductService loanProductService,
                                   UnderwritingService underwritingService,
                                   OfferService offerService,
                                   DisbursementService disbursementService,
                                   ObjectMapper objectMapper) {
        this.loanServiceContext = loanServiceContext;
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.loanProductService = loanProductService;
        this.underwritingService = underwritingService;
        this.offerService = offerService;
        this.disbursementService = disbursementService;
        this.objectMapper = objectMapper;
    }

    public LoanResponse apply(LoanRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException(request.getCustomerId()));
        LoanProduct product = loanProductService.getActiveEntity(request.getProductType());
        validateAgainstProduct(product, request.getRequestedAmount(), request.getTenureMonths());

        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .referenceNumber(allocateReferenceNumber())
                .customerId(customer.getId())
                .productId(product.getId())
                .productType(request.getProductType())
                .applicantName(customer.getFullName())
                .requestedAmount(request.getRequestedAmount())
                .tenureMonths(request.getTenureMonths())
                .status(ApplicationStatus.DRAFT)
                .requestDetails(toMap(request))
                .createdAt(Instant.now())
                .build();

        repository.save(application);
        return toResponse(application);
    }

    /**
     * Moves a {@code DRAFT} application through {@code SUBMITTED} into {@code UNDERWRITING},
     * where the existing {@link LoanProcessingStrategy} decisioning runs. An {@code APPROVED}
     * decision advances to {@code OFFERED}, {@code DECLINED} advances to {@code DECLINED}, and
     * {@code REFERRED} parks the application at {@code UNDERWRITING} for manual review.
     */
    public LoanResponse submit(String referenceNumber) {
        LoanApplication application = getEntity(referenceNumber);

        ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.SUBMITTED);
        application.setStatus(ApplicationStatus.SUBMITTED);

        ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.UNDERWRITING);
        application.setStatus(ApplicationStatus.UNDERWRITING);

        runUnderwriting(application);

        repository.save(application);
        return toResponse(application);
    }

    /**
     * Re-runs underwriting for an application parked at {@code UNDERWRITING} with a
     * {@code REFERRED} decision, recording a new {@link UnderwritingAttempt} (next cycle number)
     * instead of overwriting the referred one. Only reachable from that exact state.
     */
    public LoanResponse retryUnderwriting(String referenceNumber) {
        LoanApplication application = getEntity(referenceNumber);

        if (application.getStatus() != ApplicationStatus.UNDERWRITING
                || application.getDecision() != DecisionStatus.REFERRED) {
            throw new UnderwritingNotRetryableException(
                    referenceNumber, application.getStatus(), application.getDecision());
        }

        runUnderwriting(application);

        repository.save(application);
        return toResponse(application);
    }

    /**
     * Runs one underwriting attempt for an application already at {@code UNDERWRITING}: starts
     * an {@link UnderwritingAttempt}, runs the existing {@link LoanProcessingStrategy} unchanged,
     * completes the attempt with the outcome, and updates the application's decision/status.
     * Shared by {@link #submit} (the first attempt) and {@link #retryUnderwriting} (later ones).
     */
    private void runUnderwriting(LoanApplication application) {
        UnderwritingAttempt attempt = underwritingService.startAttempt(application.getId(), application.getStatus());

        LoanRequest request = objectMapper.convertValue(application.getRequestDetails(), LoanRequest.class);
        LoanProcessingStrategy strategy = loanServiceContext.getStrategy(application.getProductType());
        LoanResponse decision = strategy.processLoan(request);
        decision.setApplicationReference(application.getReferenceNumber());

        underwritingService.completeAttempt(
                attempt.getId(),
                UnderwritingAttemptStatus.valueOf(decision.getDecision().name()),
                toMap(decision));

        application.setDecision(decision.getDecision());
        application.setDecisionDetails(toMap(decision));

        if (decision.getDecision() == DecisionStatus.APPROVED) {
            ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.OFFERED);
            application.setStatus(ApplicationStatus.OFFERED);
        } else if (decision.getDecision() == DecisionStatus.DECLINED) {
            ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.DECLINED);
            application.setStatus(ApplicationStatus.DECLINED);
        }
    }

    /**
     * Sanctions the offer the customer selected via {@code OfferController.select}. Validates the
     * transition first (preserving the pre-existing "sanction before submit is an illegal
     * transition" behavior) before asking {@link OfferService} for the domain result — a missing
     * selection surfaces as {@code NoOfferSelectedException}, not as a transition error.
     */
    public LoanResponse sanction(String referenceNumber) {
        LoanApplication application = getEntity(referenceNumber);
        ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.SANCTIONED);

        offerService.sanctionSelectedOffer(application.getId());

        application.setStatus(ApplicationStatus.SANCTIONED);
        repository.save(application);
        return toResponse(application);
    }

    /**
     * Records one disbursement tranche. Unlike {@link #sanction}, the {@code SANCTIONED ->
     * DISBURSED} transition only fires once {@link DisbursementService} reports the running total
     * has reached the sanctioned amount; earlier tranches leave the application at
     * {@code SANCTIONED} so further tranches remain possible.
     */
    public DisbursementTrancheResponse disburse(String referenceNumber, DisbursementTrancheRequest request) {
        LoanApplication application = getEntity(referenceNumber);
        if (application.getStatus() != ApplicationStatus.SANCTIONED) {
            throw new IllegalApplicationTransitionException(application.getStatus(), ApplicationStatus.DISBURSED);
        }

        DisbursementTrancheResponse response = disbursementService.recordTranche(application.getId(), request);

        if (response.getDisbursementStatus() == DisbursementStatus.COMPLETED) {
            ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.DISBURSED);
            application.setStatus(ApplicationStatus.DISBURSED);
            repository.save(application);
        }

        response.setApplicationReference(referenceNumber);
        response.setApplicationStatus(application.getStatus());
        return response;
    }

    @Transactional(readOnly = true)
    public LoanResponse getResponse(String referenceNumber) {
        return toResponse(getEntity(referenceNumber));
    }

    private LoanApplication getEntity(String referenceNumber) {
        return repository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
    }

    private void validateAgainstProduct(LoanProduct product, BigDecimal amount, int tenureMonths) {
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0) {
            throw new LoanProductConstraintViolationException(
                    "requestedAmount must be between " + product.getMinAmount() + " and " + product.getMaxAmount()
                            + " for product " + product.getProductType());
        }
        if (tenureMonths < product.getMinTenureMonths() || tenureMonths > product.getMaxTenureMonths()) {
            throw new LoanProductConstraintViolationException(
                    "tenureMonths must be between " + product.getMinTenureMonths() + " and "
                            + product.getMaxTenureMonths() + " for product " + product.getProductType());
        }
    }

    private String allocateReferenceNumber() {
        long sequenceValue = repository.nextReferenceSequenceValue();
        return REFERENCE_PREFIX + String.format("%0" + REFERENCE_DIGITS + "d", sequenceValue);
    }

    /**
     * Converts a polymorphic DTO to a plain map via the same {@link ObjectMapper} (and
     * therefore the same {@code @JsonTypeInfo} configuration) used on the HTTP layer, so
     * the discriminator field is preserved byte-for-byte in the persisted JSONB.
     */
    private Map<String, Object> toMap(Object dto) {
        return objectMapper.convertValue(dto, new TypeReference<>() {
        });
    }

    /**
     * Builds the response from stored {@code decisionDetails} once underwriting has run;
     * before that (DRAFT/SUBMITTED/UNDERWRITING pending) only the common, decision-free
     * fields are populated.
     */
    private LoanResponse toResponse(LoanApplication application) {
        LoanResponse response;
        if (application.getDecisionDetails() != null) {
            response = objectMapper.convertValue(application.getDecisionDetails(), LoanResponse.class);
        } else {
            response = switch (application.getProductType()) {
                case PERSONAL -> new PersonalLoanResponse();
                case VEHICLE -> new VehicleLoanResponse();
            };
            response.setApplicationReference(application.getReferenceNumber());
            response.setTenureMonths(application.getTenureMonths());
        }
        response.setStatus(application.getStatus());
        response.setDecision(application.getDecision());
        return response;
    }
}
