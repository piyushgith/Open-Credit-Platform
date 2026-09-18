package com.opencredit.platform.document.repository;

import com.opencredit.platform.document.model.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanDocumentRepository extends JpaRepository<LoanDocument, UUID> {

    List<LoanDocument> findAllByApplicationId(UUID applicationId);

    Optional<LoanDocument> findByIdAndApplicationId(UUID id, UUID applicationId);
}
