package com.opencredit.platform.kyc;

import com.opencredit.platform.kyc.dto.KycCaseResponse;
import com.opencredit.platform.kyc.exception.DuplicateKycCaseException;
import com.opencredit.platform.kyc.exception.IllegalKycTransitionException;
import com.opencredit.platform.kyc.exception.KycCaseNotFoundException;
import com.opencredit.platform.kyc.model.KycCase;
import com.opencredit.platform.kyc.model.KycStatus;
import com.opencredit.platform.kyc.repository.KycCaseRepository;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class KycService {

    private final KycCaseRepository repository;
    private final LoanApplicationRepository loanApplicationRepository;

    public KycService(KycCaseRepository repository, LoanApplicationRepository loanApplicationRepository) {
        this.repository = repository;
        this.loanApplicationRepository = loanApplicationRepository;
    }

    public KycCaseResponse initiate(String referenceNumber) {
        LoanApplication application = getApplication(referenceNumber);
        if (repository.existsByApplicationId(application.getId())) {
            throw new DuplicateKycCaseException(referenceNumber);
        }

        Instant now = Instant.now();
        KycCase kycCase = KycCase.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getId())
                .status(KycStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        repository.save(kycCase);
        return toResponse(kycCase);
    }

    @Transactional(readOnly = true)
    public KycCaseResponse get(String referenceNumber) {
        return toResponse(getEntity(referenceNumber));
    }

    public KycCaseResponse verify(String referenceNumber) {
        KycCase kycCase = getEntity(referenceNumber);
        assertTransition(kycCase.getStatus(), KycStatus.VERIFIED);
        kycCase.setStatus(KycStatus.VERIFIED);
        kycCase.setUpdatedAt(Instant.now());
        repository.save(kycCase);
        return toResponse(kycCase);
    }

    public KycCaseResponse reject(String referenceNumber, String remarks) {
        KycCase kycCase = getEntity(referenceNumber);
        assertTransition(kycCase.getStatus(), KycStatus.REJECTED);
        kycCase.setStatus(KycStatus.REJECTED);
        kycCase.setRemarks(remarks);
        kycCase.setUpdatedAt(Instant.now());
        repository.save(kycCase);
        return toResponse(kycCase);
    }

    private void assertTransition(KycStatus from, KycStatus to) {
        if (from != KycStatus.PENDING) {
            throw new IllegalKycTransitionException(from, to);
        }
    }

    private LoanApplication getApplication(String referenceNumber) {
        return loanApplicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
    }

    private KycCase getEntity(String referenceNumber) {
        LoanApplication application = getApplication(referenceNumber);
        return repository.findByApplicationId(application.getId())
                .orElseThrow(() -> new KycCaseNotFoundException(referenceNumber));
    }

    private KycCaseResponse toResponse(KycCase kycCase) {
        return KycCaseResponse.builder()
                .id(kycCase.getId())
                .applicationId(kycCase.getApplicationId())
                .status(kycCase.getStatus())
                .remarks(kycCase.getRemarks())
                .createdAt(kycCase.getCreatedAt())
                .updatedAt(kycCase.getUpdatedAt())
                .build();
    }
}
