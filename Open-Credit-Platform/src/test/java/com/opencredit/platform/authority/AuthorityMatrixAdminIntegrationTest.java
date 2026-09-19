package com.opencredit.platform.authority;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the authority-matrix admin CRUD against the real local PostgreSQL database. Every
 * test creates its own entry with a high {@code matchOrder} so it never shadows the seeded default
 * rows (028-seed-default-authority-matrix.sql) that {@code ApprovalWorkflowIntegrationTest} relies
 * on for resolution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WithMockUser(roles = "ADMIN")
class AuthorityMatrixAdminIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private String createEntry(MockMvc mockMvc, int matchOrder, String requiredLevel) throws Exception {
        String requestJson = """
                {
                  "productType": "PERSONAL",
                  "riskGrade": "F",
                  "minAmount": 9000000.00,
                  "maxAmount": 9500000.00,
                  "requiredLevel": "%s",
                  "matchOrder": %d,
                  "active": true
                }
                """.formatted(requiredLevel, matchOrder);

        String responseJson = mockMvc.perform(post("/api/authority-matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return responseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    /**
     * {@code uq_authority_matrix_entry_active_match_order} (042) plus this pre-check exist because
     * {@link com.opencredit.platform.authority.support.AuthorityMatrixResolver} picks the first
     * active entry ordered by {@code matchOrder} — two active entries sharing a {@code matchOrder}
     * would make resolution depend on undefined row order.
     */
    @Test
    void creatingAnActiveEntryWithAnAlreadyUsedMatchOrderIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String entryId = createEntry(mockMvc, 9201, "CREDIT_OFFICER");

        try {
            String conflictingJson = """
                    {
                      "productType": "VEHICLE",
                      "requiredLevel": "SENIOR_CREDIT_MANAGER",
                      "matchOrder": 9201,
                      "active": true
                    }
                    """;
            mockMvc.perform(post("/api/authority-matrix")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(conflictingJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_AUTHORITY_MATRIX_MATCH_ORDER"));
        } finally {
            mockMvc.perform(delete("/api/authority-matrix/" + entryId)).andExpect(status().isOk());
        }
    }

    @Test
    void updatingAnEntryToAnAlreadyUsedActiveMatchOrderIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String firstEntryId = createEntry(mockMvc, 9202, "CREDIT_OFFICER");
        String otherEntryId = createEntry(mockMvc, 9203, "SENIOR_CREDIT_MANAGER");

        try {
            String updateJson = """
                    {
                      "productType": "PERSONAL",
                      "riskGrade": "F",
                      "minAmount": 9000000.00,
                      "maxAmount": 9500000.00,
                      "requiredLevel": "SENIOR_CREDIT_MANAGER",
                      "matchOrder": 9202,
                      "active": true
                    }
                    """;
            mockMvc.perform(put("/api/authority-matrix/" + otherEntryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_AUTHORITY_MATRIX_MATCH_ORDER"));
        } finally {
            mockMvc.perform(delete("/api/authority-matrix/" + firstEntryId)).andExpect(status().isOk());
            mockMvc.perform(delete("/api/authority-matrix/" + otherEntryId)).andExpect(status().isOk());
        }
    }

    @Test
    void createsListsGetsUpdatesAndDeletesAnEntry() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        String entryId = createEntry(mockMvc, 9001, "CREDIT_OFFICER");

        mockMvc.perform(get("/api/authority-matrix/" + entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredLevel").value("CREDIT_OFFICER"))
                .andExpect(jsonPath("$.data.productType").value("PERSONAL"));

        mockMvc.perform(get("/api/authority-matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + entryId + "')]").exists());

        String updateJson = """
                {
                  "productType": "PERSONAL",
                  "riskGrade": "F",
                  "minAmount": 9000000.00,
                  "maxAmount": 9500000.00,
                  "requiredLevel": "SENIOR_CREDIT_MANAGER",
                  "matchOrder": 9001,
                  "active": false
                }
                """;
        mockMvc.perform(put("/api/authority-matrix/" + entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredLevel").value("SENIOR_CREDIT_MANAGER"))
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(delete("/api/authority-matrix/" + entryId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/authority-matrix/" + entryId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("AUTHORITY_MATRIX_ENTRY_NOT_FOUND"));
    }
}
