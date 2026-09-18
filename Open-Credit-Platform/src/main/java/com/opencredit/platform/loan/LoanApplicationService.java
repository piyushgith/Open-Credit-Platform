package com.opencredit.platform.loan;

import com.opencredit.platform.customer.exception.CustomerNotFoundException;
import com.opencredit.platform.customer.model.Customer;
import com.opencredit.platform.customer.repository.CustomerRepository;
import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.dto.PersonalLoanResponse;
import com.opencredit.platform.loan.dto.VehicleLoanResponse;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.exception.LoanProductConstraintViolationException;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.model.LoanProduct;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.loan.strategy.LoanProcessingStrategy;
import com.opencredit.platform.loan.support.ApplicationLifecycle;
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
    private final ObjectMapper objectMapper;

    public LoanApplicationService(LoanServiceContext loanServiceContext,
                                   LoanApplicationRepository repository,
                                   CustomerRepository customerRepository,
                                   LoanProductService loanProductService,
                                   ObjectMapper objectMapper) {
        this.loanServiceContext = loanServiceContext;
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.loanProductService = loanProductService;
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

        LoanRequest request = objectMapper.convertValue(application.getRequestDetails(), LoanRequest.class);
        LoanProcessingStrategy strategy = loanServiceContext.getStrategy(application.getProductType());
        LoanResponse decision = strategy.processLoan(request);
        decision.setApplicationReference(application.getReferenceNumber());

        application.setDecision(decision.getDecision());
        application.setDecisionDetails(toMap(decision));

        if (decision.getDecision() == DecisionStatus.APPROVED) {
            ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.OFFERED);
            application.setStatus(ApplicationStatus.OFFERED);
        } else if (decision.getDecision() == DecisionStatus.DECLINED) {
            ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.DECLINED);
            application.setStatus(ApplicationStatus.DECLINED);
        }

        repository.save(application);
        return toResponse(application);
    }

    public LoanResponse sanction(String referenceNumber) {
        LoanApplication application = getEntity(referenceNumber);
        ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.SANCTIONED);
        application.setStatus(ApplicationStatus.SANCTIONED);
        repository.save(application);
        return toResponse(application);
    }

    public LoanResponse disburse(String referenceNumber) {
        LoanApplication application = getEntity(referenceNumber);
        ApplicationLifecycle.assertTransition(application.getStatus(), ApplicationStatus.DISBURSED);
        application.setStatus(ApplicationStatus.DISBURSED);
        repository.save(application);
        return toResponse(application);
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
