package com.opencredit.platform.loan.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.loan.model.DecisionStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Base type for all loan decision responses, returned as {@code ApiResponse<LoanResponse>}.
 * Carries its own {@code productType} discriminator so clients can tell which subtype
 * they received back.
 *
 * <p>Uses {@code EXISTING_PROPERTY} rather than the default {@code PROPERTY}: the abstract
 * {@link #getProductType()} getter is already a normal bean property, and pairing it with
 * plain {@code PROPERTY} would make Jackson try to write {@code productType} a second time,
 * producing a duplicate field.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "productType",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersonalLoanResponse.class, name = "PERSONAL"),
        @JsonSubTypes.Type(value = VehicleLoanResponse.class, name = "VEHICLE")
})
@Data
@NoArgsConstructor
public abstract class LoanResponse {

    private String applicationReference;
    private ApplicationStatus status;
    private DecisionStatus decision;
    private BigDecimal approvedAmount;
    private BigDecimal interestRate;
    private BigDecimal monthlyEmi;
    private Integer tenureMonths;
    private List<String> reasons = new ArrayList<>();

    public void addReason(String reason) {
        this.reasons.add(reason);
    }

    public abstract ProductType getProductType();
}
