package com.opencredit.platform.loan.repository;

import com.opencredit.platform.loan.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByReferenceNumber(String referenceNumber);

    @Query(value = "SELECT nextval('loan_reference_seq')", nativeQuery = true)
    long nextReferenceSequenceValue();
}
