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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the scorecard admin CRUD against the real local PostgreSQL database. Every test
 * creates its own scorecard(s) with a randomized name and never activates or deletes the seeded
 * {@code SME_STANDARD_V1} scorecard, so this suite never disturbs the shared "always one active
 * scorecard" state that {@code ScoringIntegrationTest} and other developers running tests
 * locally depend on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ScorecardAdminIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private static String randomName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String createScorecard(MockMvc mockMvc, String name, int minScore, int baseScore, int maxScore)
            throws Exception {
        String requestJson = """
                { "name": "%s", "baseScore": %d, "minScore": %d, "maxScore": %d }
                """.formatted(name, baseScore, minScore, maxScore);

        String responseJson = mockMvc.perform(post("/api/scorecards")
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
                """.formatted(randomName("scorecard-admin") + "@example.com",
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

    /** Applies for a personal loan, submits and analyzes one minimal financial statement, and
     * returns {@code [reference, statementId, analysisRunId]}. */
    private String[] setUpAnalyzedLoan(MockMvc mockMvc) throws Exception {
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

        return new String[] {reference, statementId, analysisRunId};
    }

    @Test
    void createUpdateAndDeleteAnUnusedInactiveScorecard() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String name = randomName("TEST_CRUD");
        String scorecardId = createScorecard(mockMvc, name, 300, 500, 900);

        mockMvc.perform(get("/api/scorecards/" + scorecardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.rules").isEmpty());

        String updatedName = randomName("TEST_CRUD_RENAMED");
        String updateJson = """
                { "name": "%s", "baseScore": 550, "minScore": 300, "maxScore": 900 }
                """.formatted(updatedName);
        mockMvc.perform(put("/api/scorecards/" + scorecardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(updatedName))
                .andExpect(jsonPath("$.data.baseScore").value(550));

        mockMvc.perform(delete("/api/scorecards/" + scorecardId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/scorecards/" + scorecardId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("SCORECARD_NOT_FOUND"));
    }

    @Test
    void invalidScoreRangeIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String requestJson = """
                { "name": "%s", "baseScore": 950, "minScore": 300, "maxScore": 900 }
                """.formatted(randomName("TEST_INVALID_RANGE"));

        mockMvc.perform(post("/api/scorecards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_SCORECARD_RANGE"));
    }

    @Test
    void duplicateScorecardNameIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String name = randomName("TEST_DUPLICATE");
        createScorecard(mockMvc, name, 300, 500, 900);

        String duplicateJson = """
                { "name": "%s", "baseScore": 500, "minScore": 300, "maxScore": 900 }
                """.formatted(name);
        mockMvc.perform(post("/api/scorecards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_SCORECARD_NAME"));
    }

    @Test
    void addingAnOverlappingRuleBandIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String scorecardId = createScorecard(mockMvc, randomName("TEST_OVERLAP"), 300, 500, 900);

        String firstRuleJson = """
                { "factorCode": "CURRENT_RATIO", "bandOrder": 1, "minValue": null, "maxValue": 2.0, "points": 40 }
                """;
        mockMvc.perform(post("/api/scorecards/" + scorecardId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRuleJson))
                .andExpect(status().isCreated());

        String overlappingRuleJson = """
                { "factorCode": "CURRENT_RATIO", "bandOrder": 2, "minValue": 1.0, "maxValue": 3.0, "points": 80 }
                """;
        mockMvc.perform(post("/api/scorecards/" + scorecardId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overlappingRuleJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("OVERLAPPING_SCORECARD_RULE"));

        String nonOverlappingRuleJson = """
                { "factorCode": "CURRENT_RATIO", "bandOrder": 2, "minValue": 2.0, "maxValue": null, "points": 80 }
                """;
        mockMvc.perform(post("/api/scorecards/" + scorecardId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonOverlappingRuleJson))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidRuleRangeIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String scorecardId = createScorecard(mockMvc, randomName("TEST_RULE_RANGE"), 300, 500, 900);

        String invalidRuleJson = """
                { "factorCode": "DSCR", "bandOrder": 1, "minValue": 2.0, "maxValue": 1.0, "points": 40 }
                """;
        mockMvc.perform(post("/api/scorecards/" + scorecardId + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRuleJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_SCORECARD_RULE_RANGE"));
    }

    @Test
    void activatingAScorecardDeactivatesThePreviouslyActiveOneAndOnlyOneSurvives() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String firstId = createScorecard(mockMvc, randomName("TEST_ACTIVATE_1"), 300, 500, 900);
        String secondId = createScorecard(mockMvc, randomName("TEST_ACTIVATE_2"), 300, 500, 900);

        try {
            mockMvc.perform(post("/api/scorecards/" + firstId + "/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true));

            mockMvc.perform(post("/api/scorecards/" + secondId + "/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(true));

            mockMvc.perform(get("/api/scorecards/" + firstId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(false));
        } finally {
            // Restore the invariant every other test (and any developer running the suite
            // locally) relies on: exactly one active scorecard, the original seeded one.
            reactivateSeededScorecard(mockMvc);
            mockMvc.perform(delete("/api/scorecards/" + firstId)).andExpect(status().isOk());
            mockMvc.perform(delete("/api/scorecards/" + secondId)).andExpect(status().isOk());
        }
    }

    @Test
    void deletingTheActiveScorecardIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String scorecardId = createScorecard(mockMvc, randomName("TEST_DELETE_ACTIVE"), 300, 500, 900);

        try {
            mockMvc.perform(post("/api/scorecards/" + scorecardId + "/activate"))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/scorecards/" + scorecardId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("SCORECARD_IN_USE"));
        } finally {
            reactivateSeededScorecard(mockMvc);
            mockMvc.perform(delete("/api/scorecards/" + scorecardId)).andExpect(status().isOk());
        }
    }

    @Test
    void deletingAScorecardAlreadyUsedToScoreAnAnalysisRunIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String scorecardId = createScorecard(mockMvc, randomName("TEST_DELETE_IN_USE"), 300, 500, 900);

        try {
            mockMvc.perform(post("/api/scorecards/" + scorecardId + "/activate"))
                    .andExpect(status().isOk());

            String[] loan = setUpAnalyzedLoan(mockMvc);
            String scoreUrl = "/api/loans/" + loan[0] + "/financial-statements/" + loan[1]
                    + "/analysis-runs/" + loan[2] + "/score";
            mockMvc.perform(post(scoreUrl)).andExpect(status().isOk());

            reactivateSeededScorecard(mockMvc);

            mockMvc.perform(delete("/api/scorecards/" + scorecardId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("SCORECARD_IN_USE"));
        } finally {
            reactivateSeededScorecard(mockMvc);
        }
    }

    /** Re-activates {@code SME_STANDARD_V1}, the scorecard every other integration test relies on. */
    private void reactivateSeededScorecard(MockMvc mockMvc) throws Exception {
        String listJson = mockMvc.perform(get("/api/scorecards"))
                .andReturn().getResponse().getContentAsString();
        int nameIndex = listJson.indexOf("SME_STANDARD_V1");
        int idKeyIndex = listJson.lastIndexOf("\"id\":\"", nameIndex);
        String seededId = listJson.substring(idKeyIndex + "\"id\":\"".length(),
                listJson.indexOf('"', idKeyIndex + "\"id\":\"".length()));

        mockMvc.perform(post("/api/scorecards/" + seededId + "/activate"))
                .andExpect(status().isOk());
    }
}
