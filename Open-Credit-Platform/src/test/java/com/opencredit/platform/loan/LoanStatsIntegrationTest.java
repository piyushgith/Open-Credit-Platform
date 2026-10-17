package com.opencredit.platform.loan;

import com.opencredit.platform.loan.dto.LoanStatsResponse;
import com.opencredit.platform.loan.model.ApplicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/loans/stats} against the real local PostgreSQL database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@WithMockUser(roles = "ADMIN")
class LoanStatsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private LoanApplicationService loanApplicationService;

    private void applyOnce(MockMvc mockMvc) throws Exception {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder pan = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            pan.append(letters.charAt(random.nextInt(letters.length())));
        }
        pan.append(String.format("%04d", random.nextInt(10_000))).append(letters.charAt(random.nextInt(26)));
        String customerResponse = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "Stats Probe", "email": "stats-%s@example.com", "phoneNumber": "9%09d",
                                  "dateOfBirth": "1995-05-15", "panNumber": "%s" }
                                """.formatted(UUID.randomUUID(), random.nextLong(1_000_000_000L), pan)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String customerId = customerResponse.split("\"id\":\"")[1].split("\"")[0];
        mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productType": "PERSONAL", "customerId": "%s", "requestedAmount": 400000.00,
                                  "tenureMonths": 60, "monthlyIncome": 500000.00, "existingEmi": 0.00,
                                  "employmentType": "SALARIED" }
                                """.formatted(customerId)))
                .andExpect(status().isCreated());
    }

    @Test
    void statsMatchTheListEndpointAndCountTodaysApplications() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        LoanStatsResponse before = loanApplicationService.stats();
        long todayBefore = before.createdPerDay().get(before.createdPerDay().size() - 1).count();
        applyOnce(mockMvc);
        LoanStatsResponse after = loanApplicationService.stats();

        assertThat(after.createdPerDay()).hasSize(14);
        assertThat(after.createdPerDay().get(13).date()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(after.createdPerDay().get(13).count()).isEqualTo(todayBefore + 1);

        assertThat(after.byStatus()).extracting(LoanStatsResponse.StatusStat::status)
                .containsExactly(ApplicationStatus.values());
        for (LoanStatsResponse.StatusStat stat : after.byStatus()) {
            long listed = loanApplicationService.listApplications(stat.status(), null, PageRequest.of(0, 1))
                    .getTotalElements();
            assertThat(stat.count()).as("count for %s", stat.status()).isEqualTo(listed);
            assertThat(stat.requestedAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
        long total = after.byStatus().stream().mapToLong(LoanStatsResponse.StatusStat::count).sum();
        assertThat(after.byProduct().stream().mapToLong(LoanStatsResponse.ProductStat::count).sum()).isEqualTo(total);

        mockMvc.perform(get("/api/loans/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byStatus.length()").value(ApplicationStatus.values().length))
                .andExpect(jsonPath("$.data.createdPerDay.length()").value(14))
                .andExpect(jsonPath("$.data.referredCount").isNumber());
    }
}
