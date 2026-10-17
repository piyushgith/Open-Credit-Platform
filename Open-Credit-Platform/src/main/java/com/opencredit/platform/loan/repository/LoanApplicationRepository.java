package com.opencredit.platform.loan.repository;

import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.DecisionStatus;
import com.opencredit.platform.loan.model.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByReferenceNumber(String referenceNumber);

    Page<LoanApplication> findAllByStatus(ApplicationStatus status, Pageable pageable);

    /** {@code pattern} must already be a LIKE pattern using {@code !} as the escape character. */
    @Query("""
            select a from LoanApplication a
            where lower(a.applicantName) like lower(:pattern) escape '!'
               or lower(a.referenceNumber) like lower(:pattern) escape '!'
            """)
    Page<LoanApplication> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select a from LoanApplication a
            where a.status = :status
              and (lower(a.applicantName) like lower(:pattern) escape '!'
                   or lower(a.referenceNumber) like lower(:pattern) escape '!')
            """)
    Page<LoanApplication> searchByStatus(@Param("status") ApplicationStatus status,
                                         @Param("pattern") String pattern, Pageable pageable);

    @Query("select a.status, count(a), coalesce(sum(a.requestedAmount), 0) from LoanApplication a group by a.status")
    List<Object[]> statusTotals();

    @Query("select a.productType, count(a) from LoanApplication a group by a.productType")
    List<Object[]> productTotals();

    /** Native: DATE(created_at AT TIME ZONE 'UTC') keeps the day buckets independent of the DB session zone. */
    @Query(value = "select cast(created_at at time zone 'UTC' as date) as d, count(*) from loan_application "
            + "where created_at >= :since group by d", nativeQuery = true)
    List<Object[]> createdPerUtcDaySince(@Param("since") Instant since);

    long countByStatusAndDecision(ApplicationStatus status, DecisionStatus decision);

    @Query(value = "SELECT nextval('loan_reference_seq')", nativeQuery = true)
    long nextReferenceSequenceValue();
}
