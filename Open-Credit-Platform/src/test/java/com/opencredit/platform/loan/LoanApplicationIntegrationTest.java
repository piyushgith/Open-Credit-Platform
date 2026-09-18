package com.opencredit.platform.loan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    /**
     * Registers a customer with randomly generated email/phone/PAN so repeated runs against
     * a persistent local Postgres (no Testcontainers yet) never collide with prior rows.
     */
    private String registerCustomer(MockMvc mockMvc) throws Exception {
        String customerJson = """
                {
                  "fullName": "Piyush Prasad",
                  "email": "%s",
                  "phoneNumber": "%s",
                  "dateOfBirth": "1995-05-15",
                  "panNumber": "%s"
                }
                """.formatted(randomEmail(), randomPhoneNumber(), randomPanNumber());

        String responseJson = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return responseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private static String randomEmail() {
        return "applicant-" + UUID.randomUUID() + "@example.com";
    }

    private static String randomPhoneNumber() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    private static String randomPanNumber() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder pan = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            pan.append(letters.charAt(random.nextInt(letters.length())));
        }
        for (int i = 0; i < 4; i++) {
            pan.append(random.nextInt(10));
        }
        pan.append(letters.charAt(random.nextInt(letters.length())));
        return pan.toString();
    }

    @Test
    void personalLoanApplicationRoundTripsThroughDatabase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);

        String requestJson = """
                {
                  "productType": "PERSONAL",
                  "customerId": "%s",
                  "requestedAmount": 500000.00,
                  "tenureMonths": 60,
                  "monthlyIncome": 120000.00,
                  "existingEmi": 15000.00,
                  "employmentType": "SALARIED"
                }
                """.formatted(customerId);

        String applyResponseJson = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.applicationReference").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.decision").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String reference = applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFERED"))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.foir").exists());

        mockMvc.perform(post("/api/loans/" + reference + "/sanction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SANCTIONED"));

        mockMvc.perform(post("/api/loans/" + reference + "/disburse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISBURSED"));
    }

    @Test
    void vehicleLoanApplicationRoundTripsThroughDatabase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);

        String requestJson = """
                {
                  "productType": "VEHICLE",
                  "customerId": "%s",
                  "requestedAmount": 800000.00,
                  "tenureMonths": 48,
                  "vehicleMake": "Honda",
                  "vehiclePrice": 1000000.00,
                  "downPayment": 200000.00,
                  "condition": "NEW"
                }
                """.formatted(customerId);

        String applyResponseJson = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productType").value("VEHICLE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        String reference = applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("VEHICLE"))
                .andExpect(jsonPath("$.data.loanToValue").exists())
                .andExpect(jsonPath("$.data.foir").doesNotExist());
    }

    @Test
    void sanctionBeforeSubmitIsRejectedAsIllegalTransition() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);

        String requestJson = """
                {
                  "productType": "PERSONAL",
                  "customerId": "%s",
                  "requestedAmount": 500000.00,
                  "tenureMonths": 60,
                  "monthlyIncome": 120000.00,
                  "existingEmi": 15000.00,
                  "employmentType": "SALARIED"
                }
                """.formatted(customerId);

        String applyResponseJson = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reference = applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/sanction"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ILLEGAL_APPLICATION_TRANSITION"));
    }

    @Test
    void unknownReferenceReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(get("/api/loans/LN-99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("LOAN_APPLICATION_NOT_FOUND"));
    }
}
