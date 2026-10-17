package com.opencredit.platform.loan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
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
 * {@code GET /api/loans?q=} against the real local PostgreSQL database: matches reference number or
 * applicant name case-insensitively, combines with the status filter, and treats LIKE wildcards in
 * the search text literally.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WithMockUser(roles = "ADMIN")
class LoanSearchIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

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

    private String applyAs(MockMvc mockMvc, String fullName) throws Exception {
        String customerJson = """
                {
                  "fullName": "%s",
                  "email": "search-%s@example.com",
                  "phoneNumber": "9%09d",
                  "dateOfBirth": "1995-05-15",
                  "panNumber": "%s"
                }
                """.formatted(fullName, UUID.randomUUID(), ThreadLocalRandom.current().nextLong(1_000_000_000L),
                randomPanNumber());
        String customerResponse = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(customerJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String customerId = customerResponse.split("\"id\":\"")[1].split("\"")[0];

        String loanJson = """
                {
                  "productType": "PERSONAL",
                  "customerId": "%s",
                  "requestedAmount": 400000.00,
                  "tenureMonths": 60,
                  "monthlyIncome": 500000.00,
                  "existingEmi": 0.00,
                  "employmentType": "SALARIED"
                }
                """.formatted(customerId);
        String loanResponse = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON).content(loanJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return loanResponse.split("\"applicationReference\":\"")[1].split("\"")[0];
    }

    @Test
    void searchMatchesApplicantNameAndReferenceCaseInsensitivelyAndCombinesWithStatus() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String unique = "Zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String reference = applyAs(mockMvc, "Searchable " + unique);

        mockMvc.perform(get("/api/loans").param("q", unique.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].applicationReference").value(reference));

        mockMvc.perform(get("/api/loans").param("q", reference.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].applicationReference").value(reference));

        mockMvc.perform(get("/api/loans").param("q", unique).param("status", "DRAFT"))
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
        mockMvc.perform(get("/api/loans").param("q", unique).param("status", "OFFERED"))
                .andExpect(jsonPath("$.data.page.totalElements").value(0));

        mockMvc.perform(get("/api/loans").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    void wildcardsInTheSearchTextAreTreatedLiterally() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        applyAs(mockMvc, "Wildcard Probe");

        mockMvc.perform(get("/api/loans").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(0));
        mockMvc.perform(get("/api/loans").param("q", "Wildcard_Probe"))
                .andExpect(jsonPath("$.data.page.totalElements").value(0));
    }
}
