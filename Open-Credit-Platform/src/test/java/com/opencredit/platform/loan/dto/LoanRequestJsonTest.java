package com.opencredit.platform.loan.dto;

import com.opencredit.platform.loan.ProductType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the polymorphic {@code productType} discriminator round-trips correctly
 * without relying on Spring's autoconfigured {@code ObjectMapper} bean, since this
 * project runs on the Jackson 3 ({@code tools.jackson}) engine shipped with Spring
 * Boot 4.1.1.
 */
class LoanRequestJsonTest {

    private final JsonMapper mapper = new JsonMapper();

    @Test
    void deserializesPersonalLoanRequestFromDiscriminator() {
        String json = """
                {
                  "productType": "PERSONAL",
                  "applicantName": "Piyush Prasad",
                  "requestedAmount": 500000.00,
                  "tenureMonths": 60,
                  "monthlyIncome": 120000.00,
                  "existingEmi": 15000.00,
                  "employmentType": "SALARIED"
                }
                """;

        LoanRequest request = mapper.readValue(json, LoanRequest.class);

        assertThat(request).isInstanceOf(PersonalLoanRequest.class);
        assertThat(request.getProductType()).isEqualTo(ProductType.PERSONAL);
        assertThat(((PersonalLoanRequest) request).getEmploymentType()).isEqualTo(EmploymentType.SALARIED);
    }

    @Test
    void deserializesVehicleLoanRequestFromDiscriminator() {
        String json = """
                {
                  "productType": "VEHICLE",
                  "applicantName": "Piyush Prasad",
                  "requestedAmount": 800000.00,
                  "tenureMonths": 48,
                  "vehicleMake": "Honda",
                  "vehiclePrice": 1000000.00,
                  "downPayment": 200000.00,
                  "condition": "NEW"
                }
                """;

        LoanRequest request = mapper.readValue(json, LoanRequest.class);

        assertThat(request).isInstanceOf(VehicleLoanRequest.class);
        assertThat(request.getProductType()).isEqualTo(ProductType.VEHICLE);
        assertThat(((VehicleLoanRequest) request).getVehicleMake()).isEqualTo("Honda");
    }

    @Test
    void unknownProductTypeFailsWithInvalidTypeId() {
        String json = """
                {
                  "productType": "HOME",
                  "applicantName": "X",
                  "requestedAmount": 100000,
                  "tenureMonths": 12
                }
                """;

        assertThatThrownBy(() -> mapper.readValue(json, LoanRequest.class))
                .isInstanceOf(InvalidTypeIdException.class);
    }

    @Test
    void personalLoanResponseSerializesWithDiscriminatorExactlyOnce() {
        PersonalLoanResponse response = new PersonalLoanResponse();
        response.setApplicationReference("LN-00000001");
        response.setDecision(com.opencredit.platform.loan.model.DecisionStatus.APPROVED);
        response.setApprovedAmount(new BigDecimal("500000.00"));
        response.setInterestRate(new BigDecimal("11.5"));
        response.setMonthlyEmi(new BigDecimal("10000.00"));
        response.setTenureMonths(60);
        response.setFoir(new BigDecimal("0.40"));

        String json = mapper.writeValueAsString((LoanResponse) response);

        assertThat(json).contains("\"productType\":\"PERSONAL\"");
        assertThat(countOccurrences(json, "\"productType\"")).isEqualTo(1);
    }

    @Test
    void vehicleLoanResponseSerializesWithDiscriminatorExactlyOnce() {
        VehicleLoanResponse response = new VehicleLoanResponse();
        response.setApplicationReference("LN-00000002");
        response.setDecision(com.opencredit.platform.loan.model.DecisionStatus.APPROVED);
        response.setApprovedAmount(new BigDecimal("800000.00"));
        response.setInterestRate(new BigDecimal("9.5"));
        response.setMonthlyEmi(new BigDecimal("20000.00"));
        response.setTenureMonths(48);
        response.setLoanToValue(new BigDecimal("0.80"));
        response.setDownPayment(new BigDecimal("200000.00"));

        String json = mapper.writeValueAsString((LoanResponse) response);

        assertThat(json).contains("\"productType\":\"VEHICLE\"");
        assertThat(countOccurrences(json, "\"productType\"")).isEqualTo(1);
    }

    private static int countOccurrences(String text, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
