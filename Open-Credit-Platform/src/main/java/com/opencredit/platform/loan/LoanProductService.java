package com.opencredit.platform.loan;

import com.opencredit.platform.loan.dto.LoanProductResponse;
import com.opencredit.platform.loan.exception.LoanProductNotFoundException;
import com.opencredit.platform.loan.model.LoanProduct;
import com.opencredit.platform.loan.repository.LoanProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LoanProductService {

    private final LoanProductRepository repository;

    public LoanProductService(LoanProductRepository repository) {
        this.repository = repository;
    }

    public List<LoanProductResponse> findAllActive() {
        return repository.findAllByActiveTrue().stream().map(this::toResponse).toList();
    }

    public LoanProductResponse findByProductType(ProductType productType) {
        return toResponse(getActiveEntity(productType));
    }

    public LoanProduct getActiveEntity(ProductType productType) {
        return repository.findByProductTypeAndActiveTrue(productType)
                .orElseThrow(() -> new LoanProductNotFoundException(productType));
    }

    private LoanProductResponse toResponse(LoanProduct product) {
        return LoanProductResponse.builder()
                .id(product.getId())
                .productType(product.getProductType())
                .name(product.getName())
                .minAmount(product.getMinAmount())
                .maxAmount(product.getMaxAmount())
                .minTenureMonths(product.getMinTenureMonths())
                .maxTenureMonths(product.getMaxTenureMonths())
                .active(product.isActive())
                .build();
    }
}
