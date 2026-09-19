package com.opencredit.platform.authority;

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
 * Exercises the full maker-checker stack (controller -&gt; service -&gt; repository) against the
 * real local PostgreSQL database, using the seeded authority matrix
 * (028-seed-default-authority-matrix.sql: &lt;=500k at grade A/B -&gt; AUTO, &lt;=2M -&gt; CREDIT_OFFICER,
 * else SENIOR_CREDIT_MANAGER) — same style/DB as {@code DecisionIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApprovalWorkflowIntegrationTest {

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
        return "approval-" + UUID.randomUUID() + "@example.com";
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

    private String applyForPersonalLoan(MockMvc mockMvc, String customerId, String requestedAmount) throws Exception {
        String requestJson = """
                {
                  "productType": "PERSONAL",
                  "customerId": "%s",
                  "requestedAmount": %s,
                  "tenureMonths": 60,
                  "monthlyIncome": 500000.00,
                  "existingEmi": 0.00,
                  "employmentType": "SALARIED"
                }
                """.formatted(customerId, requestedAmount);

        String applyResponseJson = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];
    }

    /** Same fixed line items {@code DecisionIntegrationTest} uses for its "strong financials" case: score 860, grade A. */
    private String submitAndAnalyzeStrongStatement(MockMvc mockMvc, String reference) throws Exception {
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

    /** Decides the strong-financials score (grade A, APPROVE) for a loan of the given amount, returning the decision id. */
    private String decideForAmount(MockMvc mockMvc, String requestedAmount) throws Exception {
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId, requestedAmount);
        String statementId = submitAndAnalyzeStrongStatement(mockMvc, reference);
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);

        String decisionUrl = "/api/loans/" + reference + "/financial-statements/" + statementId
                + "/analysis-runs/" + analysisRunId + "/score/" + scoreId + "/decision";
        String decisionResponseJson = mockMvc.perform(post(decisionUrl))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
        return decisionResponseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private String openCase(MockMvc mockMvc, String decisionId) throws Exception {
        String responseJson = mockMvc.perform(post("/api/credit-decisions/" + decisionId + "/approval-case"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return responseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private static String actionJson(String username, String level, String outcome) {
        return """
                { "actorUsername": "%s", "actorLevel": "%s", "outcome": "%s" }
                """.formatted(username, level, outcome);
    }

    /** Amount 1,500,000: over the 500k AUTO ceiling, under the 2M CREDIT_OFFICER ceiling. */
    @Test
    void makerThenSufficientCheckerApprovesTheCase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        mockMvc.perform(get("/api/credit-decisions/" + decisionId + "/approval-case"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredLevel").value("CREDIT_OFFICER"))
                .andExpect(jsonPath("$.data.status").value("PENDING_MAKER"));

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("alice", "CREDIT_OFFICER", "APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_CHECKER"));

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("bob", "SENIOR_CREDIT_MANAGER", "APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decisions.length()").value(2));
    }

    @Test
    void checkerRejectionLeavesTheCaseInHistoryAsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("alice", "CREDIT_OFFICER", "APPROVE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("bob", "SENIOR_CREDIT_MANAGER", "REJECT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/approval-cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.decisions[?(@.role=='CHECKER' && @.outcome=='REJECT')]").exists());
    }

    @Test
    void checkerCannotBeTheSamePersonAsTheMaker() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("alice", "CREDIT_OFFICER", "APPROVE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("alice", "SENIOR_CREDIT_MANAGER", "APPROVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_APPROVAL_NOT_ALLOWED"));
    }

    @Test
    void checkerWithInsufficientAuthorityIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("alice", "CREDIT_OFFICER", "APPROVE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("bob", "AUTO", "APPROVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_APPROVAL_AUTHORITY"));
    }

    @Test
    void theSameRoleCannotActTwiceOnACase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("alice", "CREDIT_OFFICER", "APPROVE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("alice", "CREDIT_OFFICER", "APPROVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ILLEGAL_APPROVAL_TRANSITION"));
    }

    /** Amount 500,000 at grade A resolves to AUTO under the seeded matrix -> no case may be opened. */
    @Test
    void openingACaseIsRejectedWhenTheResolvedAuthorityIsAuto() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "500000.00");

        mockMvc.perform(post("/api/credit-decisions/" + decisionId + "/approval-case"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("APPROVAL_NOT_REQUIRED"));
    }

    @Test
    void openingACaseTwiceForTheSameDecisionIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        openCase(mockMvc, decisionId);

        mockMvc.perform(post("/api/credit-decisions/" + decisionId + "/approval-case"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_APPROVAL_CASE"));
    }
}
