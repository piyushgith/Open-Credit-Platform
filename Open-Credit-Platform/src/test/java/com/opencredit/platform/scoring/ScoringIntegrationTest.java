package com.opencredit.platform.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full stack (controller -&gt; service -&gt; repository) against the real local
 * PostgreSQL database, scoring against the seeded {@code SME_STANDARD_V1} scorecard
 * (021-seed-default-scorecard.sql) — same style/DB as {@code LoanApplicationIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ScoringIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

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
        return "scoring-" + UUID.randomUUID() + "@example.com";
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

    private String applyForPersonalLoan(MockMvc mockMvc, String customerId, String monthlyIncome, String existingEmi)
            throws Exception {
        String requestJson = """
                {
                  "productType": "PERSONAL",
                  "customerId": "%s",
                  "requestedAmount": 500000.00,
                  "tenureMonths": 60,
                  "monthlyIncome": %s,
                  "existingEmi": %s,
                  "employmentType": "SALARIED"
                }
                """.formatted(customerId, monthlyIncome, existingEmi);

        String applyResponseJson = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];
    }

    private String applyForVehicleLoan(MockMvc mockMvc, String customerId) throws Exception {
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
                .andReturn().getResponse().getContentAsString();

        return applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];
    }

    /**
     * Same line items as {@code LoanApplicationIntegrationTest#financialStatementSubmissionAndAnalysisForALoanApplication}:
     * CURRENT_RATIO=2.5000, DEBT_TO_EBITDA=0.5172, INTEREST_COVERAGE=14.5000, DSCR=4.1429, no
     * previous period so DECLINING_REVENUE is never evaluated for this run.
     */
    private String submitAndAnalyzeStatement(MockMvc mockMvc, String reference) throws Exception {
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
                    { "lineItemCode": "EQUITY", "value": 400000.00 },
                    { "lineItemCode": "LONG_TERM_DEBT", "value": 100000.00 },
                    { "lineItemCode": "SHORT_TERM_DEBT", "value": 50000.00 },
                    { "lineItemCode": "INVENTORY", "value": 100000.00 },
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
                .andReturn().getResponse().getContentAsString();
        String statementId = submitResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements/" + statementId + "/analyze"))
                .andExpect(status().isOk());

        return statementId;
    }

    /**
     * DEBT_TO_EBITDA=0.5172, INTEREST_COVERAGE=14.5, DSCR=4.1429, CURRENT_RATIO=2.5 each land in
     * the top +80 band; monthlyIncome=120000/existingEmi=15000 gives EMI_TO_INCOME=0.1250, also
     * +80 -> wait, that band is <0.30 => +40. Total = 500 base + 80*4 + 40 = 860 -> grade A.
     * DECLINING_REVENUE is never evaluated (no previous period), so it contributes nothing.
     */
    @Test
    void scoringAPersonalLoanAnalysisRunPersistsAScoreWithContributingFactors() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId, "120000.00", "15000.00");
        String statementId = submitAndAnalyzeStatement(mockMvc, reference);

        String scoreUrl = "/api/loans/" + reference + "/financial-statements/" + statementId
                + "/analysis-runs/" + latestAnalysisRunId(mockMvc, reference, statementId) + "/score";

        mockMvc.perform(post(scoreUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scorecardName").value("SME_STANDARD_V1"))
                .andExpect(jsonPath("$.data.totalScore").value(860))
                .andExpect(jsonPath("$.data.riskGrade").value("A"))
                .andExpect(jsonPath("$.data.contributingFactors.length()").value(5))
                .andExpect(jsonPath("$.data.failedFactors.length()").value(0));

        mockMvc.perform(post(scoreUrl))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_SCORE"));
    }

    @Test
    void vehicleLoanWithNoIncomeDetailsIsScoredWithoutTheEmiToIncomeFactor() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForVehicleLoan(mockMvc, customerId);
        String statementId = submitAndAnalyzeStatement(mockMvc, reference);

        String scoreUrl = "/api/loans/" + reference + "/financial-statements/" + statementId
                + "/analysis-runs/" + latestAnalysisRunId(mockMvc, reference, statementId) + "/score";

        mockMvc.perform(post(scoreUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScore").value(820))
                .andExpect(jsonPath("$.data.contributingFactors.length()").value(4))
                .andExpect(jsonPath("$.data.contributingFactors[*].factorCode")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("EMI_TO_INCOME"))));
    }

    @Test
    void highDebtBurdenPersonalLoanProducesAFailedFactor() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        // existingEmi/monthlyIncome = 40000/50000 = 0.80 -> EMI_TO_INCOME's worst band (-80).
        String reference = applyForPersonalLoan(mockMvc, customerId, "50000.00", "40000.00");
        String statementId = submitAndAnalyzeStatement(mockMvc, reference);

        String scoreUrl = "/api/loans/" + reference + "/financial-statements/" + statementId
                + "/analysis-runs/" + latestAnalysisRunId(mockMvc, reference, statementId) + "/score";

        mockMvc.perform(post(scoreUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScore").value(740))
                .andExpect(jsonPath("$.data.riskGrade").value("B"))
                .andExpect(jsonPath("$.data.failedFactors.length()").value(1))
                .andExpect(jsonPath("$.data.failedFactors[0].factorCode").value("EMI_TO_INCOME"))
                .andExpect(jsonPath("$.data.failedFactors[0].description").value("High existing debt burden"));
    }

    private String latestAnalysisRunId(MockMvc mockMvc, String reference, String statementId) throws Exception {
        String analysisJson = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/loans/" + reference + "/financial-statements/" + statementId + "/analysis"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return analysisJson.split("\"id\":\"")[1].split("\"")[0];
    }
}
