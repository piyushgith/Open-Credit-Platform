package com.opencredit.platform.loan.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencredit.platform.loan.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Base type for all loan application requests. The {@code productType} JSON field
 * (already present in the payload) is used verbatim as the polymorphic discriminator,
 * so Jackson never strips it out and {@link #getProductType()} always reflects what
 * was actually deserialized.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "productType",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersonalLoanRequest.class, name = "PERSONAL"),
        @JsonSubTypes.Type(value = VehicleLoanRequest.class, name = "VEHICLE")
})
@Data
public abstract class LoanRequest {

    @NotBlank
    private String applicantName;

    @NotNull
    @DecimalMin(value = "10000.00", message = "requestedAmount must be at least 10000.00")
    private BigDecimal requestedAmount;

    @NotNull
    @Min(value = 6, message = "tenureMonths must be at least 6")
    @Max(value = 360, message = "tenureMonths must be at most 360")
    private Integer tenureMonths;

    /**
     * Returns the concrete product type. Implemented as a hardcoded constant per
     * subclass so the JSON discriminator and the Java type can never disagree.
     */
    public abstract ProductType getProductType();
}
