package com.opencredit.platform.loan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full stack (controller -&gt; service -&gt; strategy -&gt; repository) against
 * the real local PostgreSQL database configured in application.properties. Requires the
 * {@code open_credit} database to exist and be reachable; see my_plan/backend-implementation-plan.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LoanApplicationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void personalLoanApplicationRoundTripsThroughDatabase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        String requestJson = """
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

        String responseJson = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.applicationReference").exists())
                .andReturn().getResponse().getContentAsString();

        String reference = responseJson.split("\"applicationReference\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.applicationReference").value(reference));
    }

    @Test
    void vehicleLoanApplicationRoundTripsThroughDatabase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        String requestJson = """
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

        mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productType").value("VEHICLE"))
                .andExpect(jsonPath("$.data.loanToValue").exists())
                .andExpect(jsonPath("$.data.foir").doesNotExist());
    }

    @Test
    void unknownReferenceReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(get("/api/loans/LN-99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("LOAN_APPLICATION_NOT_FOUND"));
    }
}
