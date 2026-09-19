package com.opencredit.platform.authority.support;

import com.opencredit.platform.authority.model.AuthorityMatrixEntry;
import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.scoring.model.RiskGrade;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves which {@link AuthorityMatrixEntry} governs a given loan characteristic triple. Pure and
 * stateless, decoupled from persistence, mirroring {@code decision.support.DecisionEngine}: takes
 * whatever entries the caller passes in, evaluates them, returns the first match. Each of an
 * entry's criteria is optional (null = "any"); every non-null criterion on an entry must match for
 * that entry to apply. Entries are evaluated in ascending {@code matchOrder}, so a more specific
 * entry should be given a lower {@code matchOrder} than a broader catch-all.
 */
public final class AuthorityMatrixResolver {

    private AuthorityMatrixResolver() {
    }

    public static Optional<AuthorityMatrixEntry> resolve(List<AuthorityMatrixEntry> entries, ProductType productType,
                                                           RiskGrade riskGrade, BigDecimal amount) {
        return entries.stream()
                .filter(AuthorityMatrixEntry::isActive)
                .sorted(Comparator.comparingInt(AuthorityMatrixEntry::getMatchOrder))
                .filter(entry -> matches(entry, productType, riskGrade, amount))
                .findFirst();
    }

    private static boolean matches(AuthorityMatrixEntry entry, ProductType productType, RiskGrade riskGrade,
                                    BigDecimal amount) {
        if (entry.getProductType() != null && entry.getProductType() != productType) {
            return false;
        }
        if (entry.getRiskGrade() != null && entry.getRiskGrade() != riskGrade) {
            return false;
        }
        if (entry.getMinAmount() != null && amount.compareTo(entry.getMinAmount()) < 0) {
            return false;
        }
        return entry.getMaxAmount() == null || amount.compareTo(entry.getMaxAmount()) <= 0;
    }
}
