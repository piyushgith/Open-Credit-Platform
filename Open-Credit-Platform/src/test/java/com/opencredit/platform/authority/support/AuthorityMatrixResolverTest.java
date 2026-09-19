package com.opencredit.platform.authority.support;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.authority.model.AuthorityMatrixEntry;
import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.scoring.model.RiskGrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorityMatrixResolverTest {

    private static AuthorityMatrixEntry entry(ProductType productType, RiskGrade riskGrade, String minAmount,
                                               String maxAmount, ApprovalLevel level, int matchOrder, boolean active) {
        return AuthorityMatrixEntry.builder()
                .id(UUID.randomUUID())
                .productType(productType)
                .riskGrade(riskGrade)
                .minAmount(minAmount == null ? null : new BigDecimal(minAmount))
                .maxAmount(maxAmount == null ? null : new BigDecimal(maxAmount))
                .requiredLevel(level)
                .matchOrder(matchOrder)
                .active(active)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void firstMatchingEntryByAscendingMatchOrderWins() {
        List<AuthorityMatrixEntry> entries = List.of(
                entry(null, RiskGrade.A, null, "500000", ApprovalLevel.AUTO, 10, true),
                entry(null, null, null, null, ApprovalLevel.SENIOR_CREDIT_MANAGER, 100, true));

        Optional<AuthorityMatrixEntry> result =
                AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.A, new BigDecimal("100000"));

        assertThat(result).isPresent();
        assertThat(result.get().getRequiredLevel()).isEqualTo(ApprovalLevel.AUTO);
    }

    @Test
    void fallsThroughToCatchAllWhenSpecificEntryDoesNotMatch() {
        List<AuthorityMatrixEntry> entries = List.of(
                entry(null, RiskGrade.A, null, "500000", ApprovalLevel.AUTO, 10, true),
                entry(null, null, null, null, ApprovalLevel.SENIOR_CREDIT_MANAGER, 100, true));

        Optional<AuthorityMatrixEntry> result =
                AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.C, new BigDecimal("100000"));

        assertThat(result).isPresent();
        assertThat(result.get().getRequiredLevel()).isEqualTo(ApprovalLevel.SENIOR_CREDIT_MANAGER);
    }

    @Test
    void productTypeCriterionMustMatchWhenSpecified() {
        List<AuthorityMatrixEntry> entries = List.of(
                entry(ProductType.VEHICLE, null, null, null, ApprovalLevel.AUTO, 10, true));

        Optional<AuthorityMatrixEntry> result =
                AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.A, new BigDecimal("1"));

        assertThat(result).isEmpty();
    }

    @Test
    void amountExactlyAtBoundsIsInclusive() {
        List<AuthorityMatrixEntry> entries = List.of(
                entry(null, null, "100000", "500000", ApprovalLevel.CREDIT_OFFICER, 10, true));

        assertThat(AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.A, new BigDecimal("100000")))
                .isPresent();
        assertThat(AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.A, new BigDecimal("500000")))
                .isPresent();
        assertThat(AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.A, new BigDecimal("99999.99")))
                .isEmpty();
        assertThat(AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.A, new BigDecimal("500000.01")))
                .isEmpty();
    }

    @Test
    void inactiveEntriesAreNeverConsidered() {
        List<AuthorityMatrixEntry> entries = List.of(
                entry(null, null, null, null, ApprovalLevel.AUTO, 1, false));

        assertThat(AuthorityMatrixResolver.resolve(entries, ProductType.PERSONAL, RiskGrade.A, new BigDecimal("1")))
                .isEmpty();
    }

    @Test
    void noEntriesAtAllResolvesToEmpty() {
        assertThat(AuthorityMatrixResolver.resolve(List.of(), ProductType.PERSONAL, RiskGrade.A, BigDecimal.ONE))
                .isEmpty();
    }
}
