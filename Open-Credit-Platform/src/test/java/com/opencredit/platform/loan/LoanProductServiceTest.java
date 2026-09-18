package com.opencredit.platform.loan;

import com.opencredit.platform.loan.dto.LoanProductResponse;
import com.opencredit.platform.loan.exception.LoanProductNotFoundException;
import com.opencredit.platform.loan.model.LoanProduct;
import com.opencredit.platform.loan.repository.LoanProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanProductServiceTest {

    @Mock
    private LoanProductRepository repository;

    private LoanProduct personalProduct() {
        return LoanProduct.builder()
                .id(UUID.randomUUID())
                .productType(ProductType.PERSONAL)
                .name("Personal Loan")
                .minAmount(new BigDecimal("10000.00"))
                .maxAmount(new BigDecimal("2000000.00"))
                .minTenureMonths(6)
                .maxTenureMonths(84)
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void findAllActiveReturnsMappedProducts() {
        when(repository.findAllByActiveTrue()).thenReturn(List.of(personalProduct()));

        List<LoanProductResponse> products = new LoanProductService(repository).findAllActive();

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getProductType()).isEqualTo(ProductType.PERSONAL);
        assertThat(products.get(0).getMinAmount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void findByProductTypeReturnsMatchingProduct() {
        when(repository.findByProductTypeAndActiveTrue(ProductType.PERSONAL))
                .thenReturn(Optional.of(personalProduct()));

        LoanProductResponse response = new LoanProductService(repository).findByProductType(ProductType.PERSONAL);

        assertThat(response.getName()).isEqualTo("Personal Loan");
    }

    @Test
    void throwsWhenNoActiveProductForType() {
        when(repository.findByProductTypeAndActiveTrue(ProductType.VEHICLE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new LoanProductService(repository).getActiveEntity(ProductType.VEHICLE))
                .isInstanceOf(LoanProductNotFoundException.class);
    }
}
