package com.opencredit.platform.loan;

import com.opencredit.platform.loan.exception.UnsupportedProductTypeException;
import com.opencredit.platform.loan.strategy.LoanProcessingStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of {@link LoanProcessingStrategy} beans, built once at startup via Spring's
 * constructor injection of {@code List<LoanProcessingStrategy>}. Gives O(1) lookup by
 * product type with no if/else chain, and no controller or service change is needed
 * when a new product's strategy bean is added.
 */
@Component
public class LoanServiceContext {

    private final Map<ProductType, LoanProcessingStrategy> strategies;

    public LoanServiceContext(List<LoanProcessingStrategy> strategies) {
        // toUnmodifiableMap throws on a duplicate key, so two beans claiming the same
        // product type fail fast at startup instead of silently shadowing one another.
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(LoanProcessingStrategy::getProductType, Function.identity()));
    }

    public LoanProcessingStrategy getStrategy(ProductType productType) {
        LoanProcessingStrategy strategy = strategies.get(productType);
        if (strategy == null) {
            throw new UnsupportedProductTypeException(productType);
        }
        return strategy;
    }
}
