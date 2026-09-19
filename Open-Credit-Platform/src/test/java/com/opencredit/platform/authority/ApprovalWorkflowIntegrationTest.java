package com.opencredit.platform.authority;

import com.opencredit.platform.audit.model.AuditEventType;
import com.opencredit.platform.audit.repository.AuditBusinessEventRepository;
import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.security.model.AppRole;
import com.opencredit.platform.security.support.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full maker-checker stack (controller -&gt; service -&gt; repository) against the
 * real local PostgreSQL database, using the seeded authority matrix
 * (028-seed-default-authority-matrix.sql: &lt;=500k at grade A/B -&gt; AUTO, &lt;=2M -&gt; CREDIT_OFFICER,
 * else SENIOR_CREDIT_MANAGER) — same style/DB as {@code DecisionIntegrationTest}.
 *
 * <p>Since Week 10, {@code actorUsername}/{@code actorLevel} are no longer request fields — they
 * come from the authenticated principal ({@code ApprovalService.currentActor}). {@link #actAsMaker}
 * / {@link #actAsChecker} set {@code SecurityContextHolder} directly rather than going through the
 * real JWT flow: this MockMvc is built with {@code webAppContextSetup(...)} (no servlet filters,
 * so a JWT header would never be read), but {@code @PreAuthorize} is enforced by an AOP proxy
 * around the service call regardless — independent of the filter chain — so a real
 * {@code SecurityContextHolder} entry is still required for these calls to succeed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApprovalWorkflowIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuditBusinessEventRepository auditBusinessEventRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void actAsMaker(String username) {
        actAs(username, AppRole.MAKER, null);
    }

    private static void actAsChecker(String username, ApprovalLevel approvalLevel) {
        actAs(username, AppRole.CHECKER, approvalLevel);
    }

    private static void actAs(String username, AppRole role, ApprovalLevel approvalLevel) {
        AuthenticatedUser principal = new AuthenticatedUser(username, role, approvalLevel);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

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

    private String decisionId(MockMvc mockMvc, String reference, String statementId, String analysisRunId,
                               String scoreId) throws Exception {
        String decisionUrl = "/api/loans/" + reference + "/financial-statements/" + statementId
                + "/analysis-runs/" + analysisRunId + "/score/" + scoreId + "/decision";
        String decisionResponseJson = mockMvc.perform(post(decisionUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return decisionResponseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    /**
     * Debt inflated to 1,300,000 against EBITDA=290,000 -> DEBT_TO_EBITDA=4.4828, over the HARD
     * 4.0 ceiling -> DECLINE, same scenario {@code DecisionIntegrationTest.highLeverageDeclines...}
     * uses.
     */
    private String submitAndAnalyzeHighLeverageStatement(MockMvc mockMvc, String reference) throws Exception {
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
                    { "lineItemCode": "LONG_TERM_DEBT", "value": 1200000.00 },
                    { "lineItemCode": "SHORT_TERM_DEBT", "value": 100000.00 },
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

    private String openCase(MockMvc mockMvc, String decisionId) throws Exception {
        String responseJson = mockMvc.perform(post("/api/credit-decisions/" + decisionId + "/approval-case"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return responseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private static String actionJson(String outcome) {
        return """
                { "outcome": "%s" }
                """.formatted(outcome);
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

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_CHECKER"));

        actAsChecker("bob.checker", ApprovalLevel.SENIOR_CREDIT_MANAGER);
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decisions.length()").value(2));
    }

    @Test
    void checkerRejectionLeavesTheCaseInHistoryAsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        actAsChecker("bob.checker", ApprovalLevel.SENIOR_CREDIT_MANAGER);
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("REJECT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/approval-cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.decisions[?(@.role=='CHECKER' && @.outcome=='REJECT')]").exists());
    }

    /**
     * The wiring point this whole suite exists to prove: a decision that needs no human sign-off
     * (grade A/B under the 500k AUTO ceiling) must not just get {@code APPROVAL_NOT_REQUIRED} back
     * — {@code ApprovalService.openCase} finalizes the application itself in that same call, so the
     * scoring/decision pipeline (Weeks 4-7) actually reaches {@code OFFERED} and an Offer becomes
     * creatable, not just a {@code CreditDecision} row that nothing ever consumes.
     */
    @Test
    void autoApprovedDecisionAdvancesTheApplicationToOfferedAndAllowsAnOffer() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId, "500000.00");
        String statementId = submitAndAnalyzeStrongStatement(mockMvc, reference);
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);
        String decisionId = decisionId(mockMvc, reference, statementId, analysisRunId, scoreId);

        mockMvc.perform(post("/api/credit-decisions/" + decisionId + "/approval-case"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("APPROVAL_NOT_REQUIRED"));

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFERED"))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tenureMonths\": 60 }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.offerAmount").value(500000.00))
                .andExpect(jsonPath("$.data.interestRate").value(11.5));
    }

    /** Same wiring point as the AUTO case above, but reached through a full maker-checker approval. */
    @Test
    void checkerApprovalAdvancesTheApplicationToOfferedAndAllowsAnOffer() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId, "1500000.00");
        String statementId = submitAndAnalyzeStrongStatement(mockMvc, reference);
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);
        String decisionId = decisionId(mockMvc, reference, statementId, analysisRunId, scoreId);
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        actAsChecker("bob.checker", ApprovalLevel.SENIOR_CREDIT_MANAGER);
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFERED"))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tenureMonths\": 60 }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.offerAmount").value(1500000.00));
    }

    /** The other terminal outcome: a checker rejection must decline the application, not just the case. */
    @Test
    void checkerRejectionDeclinesTheApplication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId, "1500000.00");
        String statementId = submitAndAnalyzeStrongStatement(mockMvc, reference);
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);
        String decisionId = decisionId(mockMvc, reference, statementId, analysisRunId, scoreId);
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        actAsChecker("bob.checker", ApprovalLevel.SENIOR_CREDIT_MANAGER);
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("REJECT")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"))
                .andExpect(jsonPath("$.data.decision").value("DECLINED"));
    }

    /** A DECLINE outcome never needs a human sign-off, regardless of resolved authority level. */
    @Test
    void declinedDecisionMovesTheApplicationToDeclined() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId, "500000.00");
        String statementId = submitAndAnalyzeHighLeverageStatement(mockMvc, reference);
        String analysisRunId = latestAnalysisRunId(mockMvc, reference, statementId);
        String scoreId = score(mockMvc, reference, statementId, analysisRunId);
        String decisionId = decisionId(mockMvc, reference, statementId, analysisRunId, scoreId);

        mockMvc.perform(post("/api/credit-decisions/" + decisionId + "/approval-case"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("APPROVAL_NOT_REQUIRED"));

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"))
                .andExpect(jsonPath("$.data.decision").value("DECLINED"));
    }

    @Test
    void checkerCannotBeTheSamePersonAsTheMaker() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        actAsChecker("alice.maker", ApprovalLevel.SENIOR_CREDIT_MANAGER);
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_APPROVAL_NOT_ALLOWED"));
    }

    @Test
    void checkerWithInsufficientAuthorityIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        actAsChecker("bob.checker", ApprovalLevel.AUTO);
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_APPROVAL_AUTHORITY"));
    }

    /**
     * {@link com.opencredit.platform.security.model.AppRole} documents ADMIN as "a universal
     * override" on checker decisions — this must hold even for an ADMIN account with no
     * {@code approvalLevel} configured (a valid row shape, since {@code approval_level} is nullable
     * and unused for MAKER-only accounts), not just the seeded demo admin that happens to carry
     * SENIOR_CREDIT_MANAGER.
     */
    @Test
    void adminOverridesInsufficientApprovalLevel() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        actAs("admin.override", AppRole.ADMIN, null);
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    /**
     * Week 12 concurrency review: {@code UNIQUE (approval_case_id, role)} is documented as the
     * real guard behind two racing checker requests, not just {@link
     * com.opencredit.platform.authority.support.ApprovalLifecycle}'s in-memory status check — this
     * proves it under an actual race between two threads (two different, sufficiently senior
     * checkers, so the only thing that can distinguish a winner from a loser is the constraint,
     * not self-approval or insufficient authority).
     */
    @Test
    void concurrentCheckerDecisionsForTheSameCaseLetOnlyOneWin() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> checkerBob = () -> checkerDecision(mockMvc, caseId, "bob.checker",
                    ApprovalLevel.CREDIT_OFFICER, barrier);
            Callable<Integer> checkerCarol = () -> checkerDecision(mockMvc, caseId, "carol.checker",
                    ApprovalLevel.SENIOR_CREDIT_MANAGER, barrier);

            Future<Integer> resultBob = executor.submit(checkerBob);
            Future<Integer> resultCarol = executor.submit(checkerCarol);

            int statusBob = resultBob.get(10, TimeUnit.SECONDS);
            int statusCarol = resultCarol.get(10, TimeUnit.SECONDS);

            assertThat(List.of(statusBob, statusCarol)).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/approval-cases/" + caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decisions.length()").value(2))
                .andExpect(jsonPath("$.data.decisions[?(@.role=='CHECKER')]").exists());
    }

    private int checkerDecision(MockMvc mockMvc, String caseId, String username, ApprovalLevel level,
                                 CyclicBarrier barrier) throws Exception {
        actAsChecker(username, level);
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/approval-cases/" + caseId + "/checker-decision")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(actionJson("APPROVE")))
                    .andReturn().getResponse().getStatus();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void theSameRoleCannotActTwiceOnACase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        actAsMaker("alice.maker");
        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approval-cases/" + caseId + "/maker-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson("APPROVE")))
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

    /** Opening a case is the most audit-sensitive step in this pipeline; it must leave a trail. */
    @Test
    void openingACaseRecordsAnAuditEvent() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String decisionId = decideForAmount(mockMvc, "1500000.00");
        String caseId = openCase(mockMvc, decisionId);

        List<AuditEventType> recordedEventTypes = auditBusinessEventRepository
                .findAllByEntityTypeAndEntityIdOrderByOccurredAtAsc("ApprovalCase", UUID.fromString(caseId))
                .stream()
                .map(event -> event.getEventType())
                .toList();
        assertThat(recordedEventTypes).contains(AuditEventType.APPROVAL_CASE_OPENED);
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
