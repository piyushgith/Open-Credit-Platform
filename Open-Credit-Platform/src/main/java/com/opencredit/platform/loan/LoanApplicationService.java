package com.opencredit.platform.loan;

import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import com.opencredit.platform.loan.strategy.LoanProcessingStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

    public LoanApplicationService(LoanServiceContext loanServiceContext,
                                   LoanApplicationRepository repository,
                                   ObjectMapper objectMapper) {
        this.loanServiceContext = loanServiceContext;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public LoanResponse apply(LoanRequest request) {
        LoanProcessingStrategy strategy = loanServiceContext.getStrategy(request.getProductType());
        LoanResponse response = strategy.processLoan(request);

        String referenceNumber = allocateReferenceNumber();
        response.setApplicationReference(referenceNumber);

        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .referenceNumber(referenceNumber)
                .productType(request.getProductType())
                .applicantName(request.getApplicantName())
                .requestedAmount(request.getRequestedAmount())
                .tenureMonths(request.getTenureMonths())
                .status(ApplicationStatus.PROCESSED)
                .decision(response.getDecision())
                .requestDetails(toMap(request))
                .decisionDetails(toMap(response))
                .createdAt(Instant.now())
                .build();

        repository.save(application);

        return response;
    }

    @Transactional(readOnly = true)
    public LoanApplication findByReference(String referenceNumber) {
        return repository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
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
}
