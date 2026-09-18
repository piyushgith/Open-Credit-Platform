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

    /**
     * FOIR = (existingEmi + emi) / monthlyIncome lands between the 0.50 approve and 0.60 refer
     * thresholds, so the first submit parks the application at UNDERWRITING with REFERRED,
     * recording underwriting attempt cycle 1. Retrying re-runs the same deterministic strategy
     * (REFERRED again) but must record a distinct cycle 2 attempt and deactivate cycle 1.
     */
    @Test
    void referredPersonalLoanCanBeRetriedRecordingANewUnderwritingAttempt() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);

        String requestJson = """
                {
                  "productType": "PERSONAL",
                  "customerId": "%s",
                  "requestedAmount": 500000.00,
                  "tenureMonths": 60,
                  "monthlyIncome": 50000.00,
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

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.data.decision").value("REFERRED"));

        mockMvc.perform(get("/api/loans/" + reference + "/underwriting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts.length()").value(1))
                .andExpect(jsonPath("$.data.attempts[0].cycleNumber").value(1))
                .andExpect(jsonPath("$.data.attempts[0].status").value("REFERRED"))
                .andExpect(jsonPath("$.data.attempts[0].active").value(true));

        mockMvc.perform(post("/api/loans/" + reference + "/underwriting/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.data.decision").value("REFERRED"));

        mockMvc.perform(get("/api/loans/" + reference + "/underwriting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts.length()").value(2))
                .andExpect(jsonPath("$.data.attempts[0].cycleNumber").value(1))
                .andExpect(jsonPath("$.data.attempts[0].active").value(false))
                .andExpect(jsonPath("$.data.attempts[1].cycleNumber").value(2))
                .andExpect(jsonPath("$.data.attempts[1].active").value(true));
    }

    @Test
    void retryingUnderwritingOnAnApprovedApplicationIsRejected() throws Exception {
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

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        mockMvc.perform(post("/api/loans/" + reference + "/underwriting/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("UNDERWRITING_NOT_RETRYABLE"));
    }

    @Test
    void kycCaseLifecycleForALoanApplication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(post("/api/loans/" + reference + "/kyc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/loans/" + reference + "/kyc"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_KYC_CASE"));

        mockMvc.perform(post("/api/loans/" + reference + "/kyc/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));

        mockMvc.perform(post("/api/loans/" + reference + "/kyc/verify"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ILLEGAL_KYC_TRANSITION"));

        mockMvc.perform(get("/api/loans/" + reference + "/kyc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    void documentLifecycleForALoanApplication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        String documentJson = """
                {
                  "documentType": "PAN",
                  "fileName": "pan-card.pdf",
                  "storageReference": "s3://documents/pan-card.pdf"
                }
                """;

        String uploadResponseJson = mockMvc.perform(post("/api/loans/" + reference + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andReturn().getResponse().getContentAsString();

        String documentId = uploadResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/loans/" + reference + "/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/loans/" + reference + "/documents/" + documentId + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));

        mockMvc.perform(post("/api/loans/" + reference + "/documents/" + documentId + "/reject"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ILLEGAL_DOCUMENT_TRANSITION"));
    }

    @Test
    void financialStatementSubmissionAndAnalysisForALoanApplication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        String statementJson = """
                {
                  "period": {
                    "periodLabel": "FY2023",
                    "periodType": "AUDITED",
                    "startDate": "2022-04-01",
                    "endDate": "2023-03-31"
                  },
                  "lineItems": [
                    { "lineItemCode": "CURRENT_ASSETS", "value": 500000.00 },
                    { "lineItemCode": "NON_CURRENT_ASSETS", "value": 300000.00 },
                    { "lineItemCode": "CURRENT_LIABILITIES", "value": 200000.00 },
                    { "lineItemCode": "NON_CURRENT_LIABILITIES", "value": 150000.00 },
                    { "lineItemCode": "LONG_TERM_DEBT", "value": 100000.00 },
                    { "lineItemCode": "SHORT_TERM_DEBT", "value": 50000.00 },
                    { "lineItemCode": "REVENUE", "value": 1000000.00 },
                    { "lineItemCode": "COST_OF_GOODS_SOLD", "value": 600000.00 },
                    { "lineItemCode": "OPERATING_EXPENSES", "value": 150000.00 },
                    { "lineItemCode": "DEPRECIATION_AMORTIZATION", "value": 40000.00 },
                    { "lineItemCode": "INTEREST_EXPENSE", "value": 20000.00 },
                    { "lineItemCode": "TAX_EXPENSE", "value": 30000.00 }
                  ]
                }
                """;

        String submitResponseJson = mockMvc.perform(post("/api/loans/" + reference + "/financial-statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statementJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.periodLabel").value("FY2023"))
                .andExpect(jsonPath("$.data.lineItems.length()").value(12))
                .andReturn().getResponse().getContentAsString();

        String statementId = submitResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/loans/" + reference + "/financial-statements/" + statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.periodType").value("AUDITED"));

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements/" + statementId + "/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facts.length()").value(8))
                // EnumMap iterates in DerivedFinancialFactCode declaration order: ... GROSS_PROFIT(4), OPERATING_PROFIT(5), EBITDA(6), NET_PROFIT(7).
                .andExpect(jsonPath("$.data.facts[4].factCode").value("GROSS_PROFIT"))
                .andExpect(jsonPath("$.data.facts[4].value").value(400000.0))
                .andExpect(jsonPath("$.data.facts[6].factCode").value("EBITDA"))
                .andExpect(jsonPath("$.data.facts[6].value").value(290000.0))
                .andExpect(jsonPath("$.data.facts[7].factCode").value("NET_PROFIT"))
                .andExpect(jsonPath("$.data.facts[7].value").value(200000.0));

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements/" + statementId + "/analyze"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/loans/" + reference + "/financial-statements/" + statementId + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void financialStatementWithInvalidPeriodDatesIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        String statementJson = """
                {
                  "period": {
                    "periodLabel": "FY2023",
                    "periodType": "PROVISIONAL",
                    "startDate": "2023-03-31",
                    "endDate": "2022-04-01"
                  },
                  "lineItems": [
                    { "lineItemCode": "REVENUE", "value": 1000000.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statementJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FINANCIAL_PERIOD"));
    }

    @Test
    void unknownFinancialStatementReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(get("/api/loans/" + reference + "/financial-statements/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("FINANCIAL_STATEMENT_NOT_FOUND"));
    }

    private String applyForPersonalLoan(MockMvc mockMvc, String customerId) throws Exception {
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

        return applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];
    }
}
