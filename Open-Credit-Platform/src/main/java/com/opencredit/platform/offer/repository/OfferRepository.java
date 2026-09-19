package com.opencredit.platform.offer.repository;

import com.opencredit.platform.offer.model.Offer;
import com.opencredit.platform.offer.model.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findAllByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    Optional<Offer> findByApplicationIdAndStatus(UUID applicationId, OfferStatus status);

    boolean existsByApplicationIdAndStatus(UUID applicationId, OfferStatus status);
}
