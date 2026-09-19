package com.opencredit.platform.decision;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the credit-policy admin CRUD against the real local PostgreSQL database. Every test
 * creates its own policy/policies with a randomized name and always restores the seeded
 * {@code STANDARD_CREDIT_POLICY_V1} as the active policy before finishing, so this suite never
 * disturbs the shared "always one active policy" state {@code DecisionIntegrationTest} and other
 * developers running tests locally depend on — same discipline as {@code ScorecardAdminIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WithMockUser(roles = "ADMIN")
class CreditPolicyAdminIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private static String randomName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String createPolicy(MockMvc mockMvc, String name) throws Exception {
        String requestJson = """
                { "name": "%s" }
                """.formatted(name);

        String responseJson = mockMvc.perform(post("/api/credit-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return responseJson.split("\"id\":\"")[1].split("\"")[0];
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
                """.formatted(randomName("policy-admin") + "@example.com",
                "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L)),
                randomPanNumber());

        String responseJson = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return responseJson.split("\"id\":\"")[1].split("\"")[0];
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

    /** Applies for a personal loan, submits+analyzes+scores one minimal financial statement, and
     * returns {@code [reference, statementId, analysisRunId, scoreId]}. */
    private String[] setUpScoredLoan(MockMvc mockMvc) throws Exception {
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

        String statementJson = """
                {
                  "period": { "periodLabel": "FY2023", "periodType": "AUDITED", "startDate": "2022-04-01", "endDate": "2023-03-31" },
                  "lineItems": [ { "lineItemCode": "REVENUE", "value": 1000000.00 } ]
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

        String analysisJson = mockMvc.perform(
                        get("/api/loans/" + reference + "/financial-statements/" + statementId + "/analysis"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String analysisRunId = analysisJson.split("\"id\":\"")[1].split("\"")[0];

        String scoreUrl = "/api/loans/" + reference + "/financial-statements/" + statementId
                + "/analysis-runs/" + analysisRunId + "/score";
        String scoreResponseJson = mockMvc.perform(post(scoreUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String scoreId = scoreResponseJson.split("\"id\":\"")[1].split("\"")[0];

        return new String[] {reference, statementId, analysisRunId, scoreId};
    }

    @Test
    void createUpdateAndDeleteAnUnusedInactivePolicy() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String name = randomName("TEST_CRUD");
        String policyId = createPolicy(mockMvc, name);

        mockMvc.perform(get("/api/credit-policies/" + policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.rules").isEmpty());

        String updatedName = randomName("TEST_CRUD_RENAMED");
        String updateJson = """
                { "name": "%s" }
                """.formatted(updatedName);
        mockMvc.perform(put("/api/credit-policies/" + policyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(updatedName));

        mockMvc.perform(delete("/api/credit-policies/" + policyId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/credit-policies/" + policyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("CREDIT_POLICY_NOT_FOUND"));
    }

    @Test
    void duplicatePolicyNameIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String name = randomName("TEST_DUPLICATE");
        createPolicy(mockMvc, name);

        String duplicateJson = """
                { "name": "%s" }
                """.formatted(name);
        mockMvc.perform(post("/api/credit-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_CREDIT_POLICY_NAME"));
    }

    @Test
    void addingASecondRuleForTheSameFactorIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String policyId = createPolicy(mockMvc, randomName("TEST_DUPLICATE_FACTOR"));

        String firstRuleJson = """
                { "factorCode": "CURRENT_RATIO", "operator": "GTE", "thresholdValue": 1.0, "severity": "SOFT", "ruleOrder": 1 }
                """;
        mockMvc.perform(post("/api/credit-policies/" + policyId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRuleJson))
                .andExpect(status().isCreated());

        String duplicateFactorJson = """
                { "factorCode": "CURRENT_RATIO", "operator": "GTE", "thresholdValue": 1.5, "severity": "HARD", "ruleOrder": 2 }
                """;
        mockMvc.perform(post("/api/credit-policies/" + policyId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateFactorJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_CREDIT_RULE_FACTOR"));

        String differentFactorJson = """
                { "factorCode": "DSCR", "operator": "GTE", "thresholdValue": 1.5, "severity": "HARD", "ruleOrder": 2 }
                """;
        mockMvc.perform(post("/api/credit-policies/" + policyId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentFactorJson))
                .andExpect(status().isCreated());
    }

    @Test
    void activatingAPolicyDeactivatesThePreviouslyActiveOneAndOnlyOneSurvives() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String firstId = createPolicy(mockMvc, randomName("TEST_ACTIVATE_1"));
        String secondId = createPolicy(mockMvc, randomName("TEST_ACTIVATE_2"));

        try {
            mockMvc.perform(post("/api/credit-policies/" + firstId + "/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true));

            mockMvc.perform(post("/api/credit-policies/" + secondId + "/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true));

            mockMvc.perform(get("/api/credit-policies/" + firstId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        } finally {
            reactivateSeededPolicy(mockMvc);
            mockMvc.perform(delete("/api/credit-policies/" + firstId)).andExpect(status().isOk());
            mockMvc.perform(delete("/api/credit-policies/" + secondId)).andExpect(status().isOk());
        }
    }

    /**
     * Week 12 concurrency review: {@code uq_credit_policy_active} is the real guard behind
     * {@code activate()}'s deactivate-then-activate flow — this proves a race between two
     * concurrent activations never leaves two policies active and never surfaces as a raw 500
     * (the loser must get a clean {@code CONCURRENT_CREDIT_POLICY_ACTIVATION} conflict instead).
     */
    @Test
    void concurrentlyActivatingTwoDifferentPoliciesLeavesExactlyOneActive() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String firstId = createPolicy(mockMvc, randomName("TEST_CONCURRENT_ACTIVATE_1"));
        String secondId = createPolicy(mockMvc, randomName("TEST_CONCURRENT_ACTIVATE_2"));

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> activateFirst = () -> activate(mockMvc, firstId, barrier);
            Callable<Integer> activateSecond = () -> activate(mockMvc, secondId, barrier);

            Future<Integer> resultA = executor.submit(activateFirst);
            Future<Integer> resultB = executor.submit(activateSecond);

            int statusA = resultA.get(10, TimeUnit.SECONDS);
            int statusB = resultB.get(10, TimeUnit.SECONDS);

            assertThat(List.of(statusA, statusB)).allMatch(s -> s == 200 || s == 409);

            String firstActive = mockMvc.perform(get("/api/credit-policies/" + firstId))
                    .andReturn().getResponse().getContentAsString();
            String secondActive = mockMvc.perform(get("/api/credit-policies/" + secondId))
                    .andReturn().getResponse().getContentAsString();
            long activeCount = List.of(firstActive, secondActive).stream()
                    .filter(json -> json.contains("\"active\":true"))
                    .count();
            assertThat(activeCount).isEqualTo(1);
        } finally {
            reactivateSeededPolicy(mockMvc);
            mockMvc.perform(delete("/api/credit-policies/" + firstId)).andExpect(status().isOk());
            mockMvc.perform(delete("/api/credit-policies/" + secondId)).andExpect(status().isOk());
        }
    }

    /**
     * {@code @WithMockUser} only seeds the {@code SecurityContextHolder} on the test's own thread,
     * not on the executor thread this callable runs on (Spring Security's default is
     * thread-local) — so the ADMIN authentication has to be set here, same as
     * {@code ApprovalWorkflowIntegrationTest.checkerDecision} does for its own concurrency test.
     */
    private int activate(MockMvc mockMvc, String policyId, CyclicBarrier barrier) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/credit-policies/" + policyId + "/activate"))
                    .andReturn().getResponse().getStatus();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deletingTheActivePolicyIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String policyId = createPolicy(mockMvc, randomName("TEST_DELETE_ACTIVE"));

        try {
            mockMvc.perform(post("/api/credit-policies/" + policyId + "/activate"))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/credit-policies/" + policyId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("CREDIT_POLICY_IN_USE"));
        } finally {
            reactivateSeededPolicy(mockMvc);
            mockMvc.perform(delete("/api/credit-policies/" + policyId)).andExpect(status().isOk());
        }
    }

    @Test
    void deletingAPolicyAlreadyUsedToDecideAScoreIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String policyId = createPolicy(mockMvc, randomName("TEST_DELETE_IN_USE"));

        try {
            mockMvc.perform(post("/api/credit-policies/" + policyId + "/activate"))
                    .andExpect(status().isOk());

            String[] loan = setUpScoredLoan(mockMvc);
            String decisionUrl = "/api/loans/" + loan[0] + "/financial-statements/" + loan[1]
                    + "/analysis-runs/" + loan[2] + "/score/" + loan[3] + "/decision";
            mockMvc.perform(post(decisionUrl)).andExpect(status().isOk());

            reactivateSeededPolicy(mockMvc);

            mockMvc.perform(delete("/api/credit-policies/" + policyId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("CREDIT_POLICY_IN_USE"));
        } finally {
            reactivateSeededPolicy(mockMvc);
        }
    }

    /** Re-activates {@code STANDARD_CREDIT_POLICY_V1}, the policy every other integration test relies on. */
    private void reactivateSeededPolicy(MockMvc mockMvc) throws Exception {
        String listJson = mockMvc.perform(get("/api/credit-policies"))
                .andReturn().getResponse().getContentAsString();
        int nameIndex = listJson.indexOf("STANDARD_CREDIT_POLICY_V1");
        int idKeyIndex = listJson.lastIndexOf("\"id\":\"", nameIndex);
        String seededId = listJson.substring(idKeyIndex + "\"id\":\"".length(),
                listJson.indexOf('"', idKeyIndex + "\"id\":\"".length()));

        mockMvc.perform(post("/api/credit-policies/" + seededId + "/activate"))
                .andExpect(status().isOk());
    }
}
