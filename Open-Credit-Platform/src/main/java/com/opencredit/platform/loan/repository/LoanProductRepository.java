package com.opencredit.platform.loan.repository;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.model.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {

    Optional<LoanProduct> findByProductTypeAndActiveTrue(ProductType productType);

    List<LoanProduct> findAllByActiveTrue();
}
