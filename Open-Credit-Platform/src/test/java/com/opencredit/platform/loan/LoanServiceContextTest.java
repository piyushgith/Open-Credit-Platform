package com.opencredit.platform.loan;

import com.opencredit.platform.loan.dto.LoanRequest;
import com.opencredit.platform.loan.dto.LoanResponse;
import com.opencredit.platform.loan.exception.UnsupportedProductTypeException;
import com.opencredit.platform.loan.strategy.LoanProcessingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanServiceContextTest {

    private static LoanProcessingStrategy strategyFor(ProductType type) {
        return new LoanProcessingStrategy() {
            @Override
            public ProductType getProductType() {
                return type;
            }

            @Override
            public LoanResponse processLoan(LoanRequest request) {
                throw new UnsupportedOperationException("not needed for this test");
            }
        };
    }

    @Test
    void resolvesEachRegisteredProductType() {
        LoanProcessingStrategy personal = strategyFor(ProductType.PERSONAL);
        LoanProcessingStrategy vehicle = strategyFor(ProductType.VEHICLE);

        LoanServiceContext context = new LoanServiceContext(List.of(personal, vehicle));

        assertThat(context.getStrategy(ProductType.PERSONAL)).isSameAs(personal);
        assertThat(context.getStrategy(ProductType.VEHICLE)).isSameAs(vehicle);
    }

    @Test
    void throwsUnsupportedProductTypeWhenNoStrategyRegistered() {
        LoanServiceContext context = new LoanServiceContext(List.of(strategyFor(ProductType.PERSONAL)));

        assertThatThrownBy(() -> context.getStrategy(ProductType.VEHICLE))
                .isInstanceOf(UnsupportedProductTypeException.class);
    }

    @Test
    void failsFastOnDuplicateProductTypeRegistration() {
        LoanProcessingStrategy first = strategyFor(ProductType.PERSONAL);
        LoanProcessingStrategy duplicate = strategyFor(ProductType.PERSONAL);

        assertThatThrownBy(() -> new LoanServiceContext(List.of(first, duplicate)))
                .isInstanceOf(IllegalStateException.class);
    }
}
