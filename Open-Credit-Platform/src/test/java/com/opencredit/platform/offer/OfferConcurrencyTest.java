package com.opencredit.platform.offer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Week 12 concurrency review: {@code Offer}'s partial unique index
 * (WHERE status = 'SELECTED') is documented as the real concurrency guard behind
 * {@code OfferService.selectOffer}'s pre-check — this proves it under an actual race between two
 * threads, not just sequential calls hitting the same pre-check twice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OfferConcurrencyTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void selectingTwoDifferentOffersConcurrentlyLetsOnlyOneWin() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyAndApprove(mockMvc, customerId);

        String offerId1 = createOffer(mockMvc, reference, 60);
        String offerId2 = createOffer(mockMvc, reference, 48);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> selectFirst = () -> select(mockMvc, reference, offerId1, barrier);
            Callable<Integer> selectSecond = () -> select(mockMvc, reference, offerId2, barrier);

            Future<Integer> resultA = executor.submit(selectFirst);
            Future<Integer> resultB = executor.submit(selectSecond);

            int statusA = resultA.get(10, TimeUnit.SECONDS);
            int statusB = resultB.get(10, TimeUnit.SECONDS);

            List<Integer> statuses = List.of(statusA, statusB);
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    private int select(MockMvc mockMvc, String reference, String offerId, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/loans/" + reference + "/offers/" + offerId + "/select"))
                .andReturn().getResponse().getStatus();
    }

    private String createOffer(MockMvc mockMvc, String reference, int tenureMonths) throws Exception {
        String responseJson = mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": " + tenureMonths + "}"))
                .andReturn().getResponse().getContentAsString();
        return responseJson.split("\"id\":\"")[1].split("\"")[0];
    }

    private String applyAndApprove(MockMvc mockMvc, String customerId) throws Exception {
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
        return "offer-concurrency-" + UUID.randomUUID() + "@example.com";
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
