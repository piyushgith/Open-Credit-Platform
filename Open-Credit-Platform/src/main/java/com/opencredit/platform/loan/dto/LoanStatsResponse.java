package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.model.ApplicationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Portfolio totals computed in the database over every application. {@code createdPerDay} covers
 * the last 14 UTC days including today, with zero-count days present.
 */
public record LoanStatsResponse(
        List<StatusStat> byStatus,
        List<ProductStat> byProduct,
        List<DayStat> createdPerDay,
        long referredCount
) {
    public record StatusStat(ApplicationStatus status, long count, BigDecimal requestedAmount) {
    }

    public record ProductStat(ProductType productType, long count) {
    }

    public record DayStat(LocalDate date, long count) {
    }
}
