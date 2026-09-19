package com.opencredit.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain end to end — login against the demo-seeded
 * {@code AppUser} rows (036-seed-demo-users.sql), then the JWT that login returns. Unlike
 * {@code ApprovalWorkflowIntegrationTest} and the admin CRUD tests (built with
 * {@code webAppContextSetup(...)}, which never runs a servlet filter), this class uses
 * {@code @AutoConfigureMockMvc}, which wires Spring Security's filter chain into MockMvc — the
 * only way to actually prove a missing/wrong-role token is rejected before reaching a controller.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String login(String username, String password) throws Exception {
        String requestJson = """
                { "username": "%s", "password": "%s" }
                """.formatted(username, password);

        String responseJson = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return responseJson.split("\"token\":\"")[1].split("\"")[0];
    }

    @Test
    void loginWithValidDemoCredentialsReturnsAUsableToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin123" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "not-the-password" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginWithUnknownUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "nobody", "password": "whatever" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void requestWithNoTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/loan-products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("UNAUTHENTICATED"));
    }

    @Test
    void requestWithAnInvalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/loan-products").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value("UNAUTHENTICATED"));
    }

    @Test
    void anyAuthenticatedUserCanReachAnEndpointWithNoSpecificRoleRequirement() throws Exception {
        String token = login("alice.maker", "maker123");

        mockMvc.perform(get("/api/loan-products").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aNonAdminIsForbiddenFromAnAdminConfigEndpoint() throws Exception {
        String token = login("alice.maker", "maker123");

        mockMvc.perform(get("/api/authority-matrix").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED"));
    }

    @Test
    void anAdminCanReachAnAdminConfigEndpoint() throws Exception {
        String token = login("admin", "admin123");

        mockMvc.perform(get("/api/authority-matrix").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void aMakerIsForbiddenFromTheCheckerDecisionEndpoint() throws Exception {
        String token = login("alice.maker", "maker123");

        mockMvc.perform(post("/api/approval-cases/" + java.util.UUID.randomUUID() + "/checker-decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "outcome": "APPROVE" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED"));
    }
}
