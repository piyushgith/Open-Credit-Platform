package com.opencredit.platform.decision;

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
 * Exercises the full stack (controller -&gt; service -&gt; repository) against the real local
 * PostgreSQL database, deciding scores against the seeded {@code STANDARD_CREDIT_POLICY_V1}
 * (026-seed-default-credit-policy.sql) — same style/DB as {@code ScoringIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DecisionIntegrationTest {

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
        return "decision-" + UUID.randomUUID() + "@example.com";
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

    /**
     * Submits and analyzes a statement whose current assets, long-term debt and short-term debt
     * are parameterized so each test can steer DEBT_TO_EBITDA/CURRENT_RATIO/DSCR toward a
     * specific outcome, while REVENUE/COGS/OPERATING_EXPENSES/DEPRECIATION_AMORTIZATION/
     * INTEREST_EXPENSE stay fixed (EBITDA = 1,000,000 - 600,000 - 150,000 + 40,000 = 290,000).
     */
    private String submitAndAnalyzeStatement(MockMvc mockMvc, String reference, String currentAssets,
                                              String longTermDebt, String shortTermDebt) throws Exception {
        String statementJson = """
                {
                  "period": {
                    "periodLabel": "FY2023",
                    "periodType": "AUDITED",
                    "startDate": "2022-04-01",
                    "endDate": "2023-03-31"
                  },
                  "lineItems": [
                    { "lineItemCode": "CURRENT_ASSETS", "value": %s },
                    { "lineItemCode": "NON_CURRENT_ASSETS", "value": 300000.00 },
                    { "lineItemCode": "CURRENT_LIABILITIES", "value": 200000.00 },
                    { "lineItemCode": "NON_CURRENT_LIABILITIES", "value": 150000.00 },
                    { "lineItemCode": "EQUITY", "value": 400000.00 },
                    { "lineItemCode": "LONG_TERM_DEBT", "value": %s },
                    { "lineItemCode": "SHORT_TERM_DEBT", "value": %s },
                    { "lineItemCode": "INVENTORY", "value": 100000.00 },
                    { "lineItemCode": "REVENUE", "value": 1000000.00 },
                    { "lineItemCode": "COST_OF_GOODS_SOLD", "value": 600000.00 },
                    { "lineItemCode": "OPERATING_EXPENSES", "value": 150000.00 },
                    { "lineItemCode": "DEPRECIATION_AMORTIZATION", "value": 40000.00 },
                    { "lineItemCode": "INTEREST_EXPENSE", "value": 20000.00 },
                    { "lineItemCode": "TAX_EXPENSE", "value": 30000.00 }
                  ]
                }
                """.formatted(currentAssets, longTermDebt, shortTermDebt);

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

    private String latestAnalysisRunId(MockMvc mockMvc, String reference, String statementId) throws Exception {
        String analysisJson = mockMvc.perform(
                        get("/api/loans/" + reference + "/financial-statements/" + statementId + "/analysis"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return analysisJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private String score(MockMvc mockMvc, String reference, String statementId, String analysisRunId) throws Exception {
        String scoreUrl = "/api/loans/" + reference + "/financial-statements/" + statementId
                + "/analysis-runs/" + analysisRunId + "/score";
        String scoreResponseJson = mockMvc.perform(post(scoreUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return scoreResponseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private String decisionUrl(String reference, String statementId, String analysisRunId, String scoreId) {
        return "/api/loans/" + reference + "/financial-statements/" + statementId + "/analysis-runs/" + analysisRunId
                + "/score/" + scoreId + "/decision";
    }

    /**
     * DEBT_TO_EBITDA=0.5172, INTEREST_COVERAGE=14.5, DSCR=4.1429, CURRENT_RATIO=2.5 -> total
     * score 860 (grade A) -> every seeded rule (3 HARD, 2 SOFT) passes -> APPROVE, AUTO authority.
     */
    @Test
    void strongFinancialsApproveUnderTheSeededPolicy() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);
        String statementId = submitAndAnalyzeStatement(mockMvc, reference, "500000.00", "100000.00", "50000.00");
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);

        String url = decisionUrl(reference, statementId, analysisRunId, scoreId);

        mockMvc.perform(post(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyName").value("STANDARD_CREDIT_POLICY_V1"))
                .andExpect(jsonPath("$.data.outcome").value("APPROVE"))
                .andExpect(jsonPath("$.data.requiredAuthority").value("AUTO"))
                .andExpect(jsonPath("$.data.passedRules.length()").value(5))
                .andExpect(jsonPath("$.data.failedRules.length()").value(0));

        mockMvc.perform(post(url))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_CREDIT_DECISION"));
    }

    /**
     * Debt inflated to 1,300,000 against EBITDA=290,000 -> DEBT_TO_EBITDA=4.4828, over the HARD
     * 4.0 ceiling -> DECLINE regardless of every other rule passing.
     */
    @Test
    void highLeverageDeclinesEvenWhenOtherRulesPass() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);
        String statementId = submitAndAnalyzeStatement(mockMvc, reference, "500000.00", "1200000.00", "100000.00");
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);

        mockMvc.perform(post(decisionUrl(reference, statementId, analysisRunId, scoreId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("DECLINE"))
                .andExpect(jsonPath("$.data.requiredAuthority").value("AUTO"))
                .andExpect(jsonPath("$.data.failedRules[?(@.factorCode=='DEBT_TO_EBITDA')]").exists());
    }

    /**
     * Current assets dropped to 100,000 against 200,000 current liabilities -> CURRENT_RATIO=0.5,
     * under the SOFT 1.0 floor, while every HARD rule (TOTAL_SCORE=700, DSCR=4.1429,
     * DEBT_TO_EBITDA=0.5172) still passes -> REFER, not DECLINE.
     */
    @Test
    void weakLiquidityAloneRefersRatherThanDeclines() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);
        String statementId = submitAndAnalyzeStatement(mockMvc, reference, "100000.00", "100000.00", "50000.00");
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);

        mockMvc.perform(post(decisionUrl(reference, statementId, analysisRunId, scoreId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("REFER"))
                .andExpect(jsonPath("$.data.requiredAuthority").value("SENIOR_CREDIT_MANAGER"))
                .andExpect(jsonPath("$.data.passedRules.length()").value(4))
                .andExpect(jsonPath("$.data.failedRules.length()").value(1))
                .andExpect(jsonPath("$.data.failedRules[0].factorCode").value("CURRENT_RATIO"));
    }
}
