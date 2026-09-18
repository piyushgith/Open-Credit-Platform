package com.opencredit.platform.document;

import com.opencredit.platform.document.dto.LoanDocumentRequest;
import com.opencredit.platform.document.dto.LoanDocumentResponse;
import com.opencredit.platform.document.exception.IllegalDocumentTransitionException;
import com.opencredit.platform.document.exception.LoanDocumentNotFoundException;
import com.opencredit.platform.document.model.DocumentStatus;
import com.opencredit.platform.document.model.LoanDocument;
import com.opencredit.platform.document.repository.LoanDocumentRepository;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.LoanApplication;
import com.opencredit.platform.loan.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LoanDocumentService {

    private final LoanDocumentRepository repository;
    private final LoanApplicationRepository loanApplicationRepository;

    public LoanDocumentService(LoanDocumentRepository repository,
                                LoanApplicationRepository loanApplicationRepository) {
        this.repository = repository;
        this.loanApplicationRepository = loanApplicationRepository;
    }

    public LoanDocumentResponse upload(String referenceNumber, LoanDocumentRequest request) {
        LoanApplication application = getApplication(referenceNumber);

        LoanDocument document = LoanDocument.builder()
                .id(UUID.randomUUID())
                .applicationId(application.getId())
                .documentType(request.getDocumentType())
                .fileName(request.getFileName())
                .storageReference(request.getStorageReference())
                .status(DocumentStatus.UPLOADED)
                .uploadedAt(Instant.now())
                .build();

        repository.save(document);
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<LoanDocumentResponse> list(String referenceNumber) {
        LoanApplication application = getApplication(referenceNumber);
        return repository.findAllByApplicationId(application.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public LoanDocumentResponse verify(String referenceNumber, UUID documentId) {
        LoanDocument document = getDocument(referenceNumber, documentId);
        assertTransition(document.getStatus(), DocumentStatus.VERIFIED);
        document.setStatus(DocumentStatus.VERIFIED);
        repository.save(document);
        return toResponse(document);
    }

    public LoanDocumentResponse reject(String referenceNumber, UUID documentId) {
        LoanDocument document = getDocument(referenceNumber, documentId);
        assertTransition(document.getStatus(), DocumentStatus.REJECTED);
        document.setStatus(DocumentStatus.REJECTED);
        repository.save(document);
        return toResponse(document);
    }

    private void assertTransition(DocumentStatus from, DocumentStatus to) {
        if (from != DocumentStatus.UPLOADED) {
            throw new IllegalDocumentTransitionException(from, to);
        }
    }

    private LoanApplication getApplication(String referenceNumber) {
        return loanApplicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new LoanApplicationNotFoundException(referenceNumber));
    }

    private LoanDocument getDocument(String referenceNumber, UUID documentId) {
        LoanApplication application = getApplication(referenceNumber);
        return repository.findByIdAndApplicationId(documentId, application.getId())
                .orElseThrow(() -> new LoanDocumentNotFoundException(documentId));
    }

    private LoanDocumentResponse toResponse(LoanDocument document) {
        return LoanDocumentResponse.builder()
                .id(document.getId())
                .applicationId(document.getApplicationId())
                .documentType(document.getDocumentType())
                .fileName(document.getFileName())
                .storageReference(document.getStorageReference())
                .status(document.getStatus())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
