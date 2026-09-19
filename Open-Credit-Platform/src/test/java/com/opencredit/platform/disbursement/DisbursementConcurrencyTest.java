package com.opencredit.platform.disbursement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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

/**
 * Week 12 concurrency review: {@code Disbursement.disbursedTotal}'s {@code @Version} optimistic
 * lock is the only thing standing between two concurrent tranche requests and a lost update — the
 * exceeds-sanctioned-amount check in {@code DisbursementService.recordTranche} reads the in-memory
 * (potentially stale) total, so without the lock, two concurrent 250,000 tranches against a 500,000
 * sanction could both read {@code disbursedTotal=0}, both pass that check, and both commit —
 * leaving a final total of 250,000 instead of 500,000 despite two successful-looking responses.
 * This proves that can't happen: whatever mix of 200/409 the race produces, the final total must
 * exactly equal 250,000 times however many requests actually won.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DisbursementConcurrencyTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void concurrentTranchesNeverLoseAnUpdateRegardlessOfWhichOneWins() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyApproveOfferAndSanction(mockMvc, customerId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> trancheA = () -> disburse(mockMvc, reference, "CONCURRENCY-TRANCHE-A", barrier);
            Callable<Integer> trancheB = () -> disburse(mockMvc, reference, "CONCURRENCY-TRANCHE-B", barrier);

            Future<Integer> resultA = executor.submit(trancheA);
            Future<Integer> resultB = executor.submit(trancheB);

            int statusA = resultA.get(10, TimeUnit.SECONDS);
            int statusB = resultB.get(10, TimeUnit.SECONDS);

            assertThat(statusA).isIn(200, 409);
            assertThat(statusB).isIn(200, 409);
            long successCount = java.util.stream.Stream.of(statusA, statusB).filter(s -> s == 200).count();

            String disbursementJson = mockMvc.perform(get("/api/loans/" + reference + "/disbursements"))
                    .andReturn().getResponse().getContentAsString();
            double disbursedTotal = Double.parseDouble(
                    disbursementJson.split("\"disbursedTotal\":")[1].split("[,}]")[0]);

            assertThat(disbursedTotal).isEqualTo(successCount * 250000.0);
        } finally {
            executor.shutdownNow();
        }
    }

    private int disburse(MockMvc mockMvc, String reference, String requestReference, CyclicBarrier barrier)
            throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/loans/" + reference + "/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 250000.00, \"requestReference\": \"" + requestReference + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private String applyApproveOfferAndSanction(MockMvc mockMvc, String customerId) throws Exception {
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
                .andReturn().getResponse().getContentAsString();
        String reference = applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/submit"));

        String offerJson = mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": 60}"))
                .andReturn().getResponse().getContentAsString();
        String offerId = offerJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/offers/" + offerId + "/select"));
        mockMvc.perform(post("/api/loans/" + reference + "/sanction"));

        return reference;
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
                .andReturn().getResponse().getContentAsString();
        return responseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private static String randomEmail() {
        return "disbursement-concurrency-" + UUID.randomUUID() + "@example.com";
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
}
