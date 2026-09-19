package com.opencredit.platform.loan;

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
 * Exercises the full stack (controller -&gt; service -&gt; strategy -&gt; repository) against
 * the real local PostgreSQL database configured in application.properties. Requires the
 * {@code open_credit} database to exist and be reachable; see my_plan/backend-implementation-plan.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LoanApplicationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    /**
     * Registers a customer with randomly generated email/phone/PAN so repeated runs against
     * a persistent local Postgres (no Testcontainers yet) never collide with prior rows.
     */
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
        return "applicant-" + UUID.randomUUID() + "@example.com";
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

    @Test
    void personalLoanApplicationRoundTripsThroughDatabase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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
                .andExpect(jsonPath("$.data.productType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.applicationReference").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.decision").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String reference = applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFERED"))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.foir").exists());

        String offerResponseJson = mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": 60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.offerAmount").value(500000.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String offerId = offerResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/offers/" + offerId + "/select"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SELECTED"));

        mockMvc.perform(post("/api/loans/" + reference + "/sanction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SANCTIONED"));

        mockMvc.perform(get("/api/loans/" + reference + "/sanction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sanctionedAmount").value(500000.00));

        mockMvc.perform(post("/api/loans/" + reference + "/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300000.00, \"requestReference\": \"TRANCHE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationStatus").value("SANCTIONED"))
                .andExpect(jsonPath("$.data.trancheNumber").value(1))
                .andExpect(jsonPath("$.data.disbursementStatus").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/loans/" + reference + "/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 200000.00, \"requestReference\": \"TRANCHE-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationStatus").value("DISBURSED"))
                .andExpect(jsonPath("$.data.trancheNumber").value(2))
                .andExpect(jsonPath("$.data.disbursementStatus").value("COMPLETED"));

        mockMvc.perform(get("/api/loans/" + reference + "/disbursements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursedTotal").value(500000.00))
                .andExpect(jsonPath("$.data.tranches.length()").value(2));

        mockMvc.perform(get("/api/loans/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISBURSED"));
    }

    @Test
    void offerCannotBeCreatedBeforeUnderwritingApproves() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": 60}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("OFFER_NOT_ELIGIBLE"));
    }

    @Test
    void secondOfferCannotBeSelectedOnceOneIsAlreadySelected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        String firstOfferJson = mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": 60}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstOfferId = firstOfferJson.split("\"id\":\"")[1].split("\"")[0];

        String secondOfferJson = mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": 48}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondOfferId = secondOfferJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/offers/" + firstOfferId + "/select"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/loans/" + reference + "/offers/" + secondOfferId + "/select"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("OFFER_ALREADY_SELECTED"));
    }

    @Test
    void sanctionWithoutASelectedOfferIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        mockMvc.perform(post("/api/loans/" + reference + "/sanction"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("NO_OFFER_SELECTED"));
    }

    @Test
    void disbursementExceedingTheSanctionedAmountIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(post("/api/loans/" + reference + "/submit")).andExpect(status().isOk());

        String offerJson = mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": 60}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String offerId = offerJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/offers/" + offerId + "/select"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/loans/" + reference + "/sanction")).andExpect(status().isOk());

        mockMvc.perform(post("/api/loans/" + reference + "/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 600000.00, \"requestReference\": \"TRANCHE-OVER\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("DISBURSEMENT_EXCEEDS_SANCTIONED_AMOUNT"));
    }

    @Test
    void duplicateDisbursementRequestReferenceIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(post("/api/loans/" + reference + "/submit")).andExpect(status().isOk());

        String offerJson = mockMvc.perform(post("/api/loans/" + reference + "/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenureMonths\": 60}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String offerId = offerJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/offers/" + offerId + "/select"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/loans/" + reference + "/sanction")).andExpect(status().isOk());

        mockMvc.perform(post("/api/loans/" + reference + "/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100000.00, \"requestReference\": \"TRANCHE-DUP\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/loans/" + reference + "/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100000.00, \"requestReference\": \"TRANCHE-DUP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_DISBURSEMENT_REQUEST"));
    }

    @Test
    void vehicleLoanApplicationRoundTripsThroughDatabase() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);

        String requestJson = """
                {
                  "productType": "VEHICLE",
                  "customerId": "%s",
                  "requestedAmount": 800000.00,
                  "tenureMonths": 48,
                  "vehicleMake": "Honda",
                  "vehiclePrice": 1000000.00,
                  "downPayment": 200000.00,
                  "condition": "NEW"
                }
                """.formatted(customerId);

        String applyResponseJson = mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productType").value("VEHICLE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        String reference = applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productType").value("VEHICLE"))
                .andExpect(jsonPath("$.data.loanToValue").exists())
                .andExpect(jsonPath("$.data.foir").doesNotExist());
    }

    @Test
    void sanctionBeforeSubmitIsRejectedAsIllegalTransition() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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

        mockMvc.perform(post("/api/loans/" + reference + "/sanction"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ILLEGAL_APPLICATION_TRANSITION"));
    }

    @Test
    void unknownReferenceReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(get("/api/loans/LN-99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("LOAN_APPLICATION_NOT_FOUND"));
    }

    /**
     * FOIR = (existingEmi + emi) / monthlyIncome lands between the 0.50 approve and 0.60 refer
     * thresholds, so the first submit parks the application at UNDERWRITING with REFERRED,
     * recording underwriting attempt cycle 1. Retrying re-runs the same deterministic strategy
     * (REFERRED again) but must record a distinct cycle 2 attempt and deactivate cycle 1.
     */
    @Test
    void referredPersonalLoanCanBeRetriedRecordingANewUnderwritingAttempt() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);

        String requestJson = """
                {
                  "productType": "PERSONAL",
                  "customerId": "%s",
                  "requestedAmount": 500000.00,
                  "tenureMonths": 60,
                  "monthlyIncome": 50000.00,
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

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.data.decision").value("REFERRED"));

        mockMvc.perform(get("/api/loans/" + reference + "/underwriting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts.length()").value(1))
                .andExpect(jsonPath("$.data.attempts[0].cycleNumber").value(1))
                .andExpect(jsonPath("$.data.attempts[0].status").value("REFERRED"))
                .andExpect(jsonPath("$.data.attempts[0].active").value(true));

        mockMvc.perform(post("/api/loans/" + reference + "/underwriting/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDERWRITING"))
                .andExpect(jsonPath("$.data.decision").value("REFERRED"));

        mockMvc.perform(get("/api/loans/" + reference + "/underwriting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts.length()").value(2))
                .andExpect(jsonPath("$.data.attempts[0].cycleNumber").value(1))
                .andExpect(jsonPath("$.data.attempts[0].active").value(false))
                .andExpect(jsonPath("$.data.attempts[1].cycleNumber").value(2))
                .andExpect(jsonPath("$.data.attempts[1].active").value(true));
    }

    @Test
    void retryingUnderwritingOnAnApprovedApplicationIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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

        mockMvc.perform(post("/api/loans/" + reference + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        mockMvc.perform(post("/api/loans/" + reference + "/underwriting/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("UNDERWRITING_NOT_RETRYABLE"));
    }

    @Test
    void kycCaseLifecycleForALoanApplication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(post("/api/loans/" + reference + "/kyc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/loans/" + reference + "/kyc"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_KYC_CASE"));

        mockMvc.perform(post("/api/loans/" + reference + "/kyc/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));

        mockMvc.perform(post("/api/loans/" + reference + "/kyc/verify"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ILLEGAL_KYC_TRANSITION"));

        mockMvc.perform(get("/api/loans/" + reference + "/kyc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));
    }

    @Test
    void documentLifecycleForALoanApplication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        String documentJson = """
                {
                  "documentType": "PAN",
                  "fileName": "pan-card.pdf",
                  "storageReference": "s3://documents/pan-card.pdf"
                }
                """;

        String uploadResponseJson = mockMvc.perform(post("/api/loans/" + reference + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(documentJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andReturn().getResponse().getContentAsString();

        String documentId = uploadResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/loans/" + reference + "/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/loans/" + reference + "/documents/" + documentId + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"));

        mockMvc.perform(post("/api/loans/" + reference + "/documents/" + documentId + "/reject"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("ILLEGAL_DOCUMENT_TRANSITION"));
    }

    @Test
    void financialStatementSubmissionAndAnalysisForALoanApplication() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

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
                .andExpect(jsonPath("$.data.period.periodLabel").value("FY2023"))
                .andExpect(jsonPath("$.data.lineItems.length()").value(14))
                .andReturn().getResponse().getContentAsString();

        String statementId = submitResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/loans/" + reference + "/financial-statements/" + statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.periodType").value("AUDITED"));

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements/" + statementId + "/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.facts.length()").value(8))
                // EnumMap iterates in DerivedFinancialFactCode declaration order: ... GROSS_PROFIT(4), OPERATING_PROFIT(5), EBITDA(6), NET_PROFIT(7).
                .andExpect(jsonPath("$.data.facts[4].factCode").value("GROSS_PROFIT"))
                .andExpect(jsonPath("$.data.facts[4].value").value(400000.0))
                .andExpect(jsonPath("$.data.facts[6].factCode").value("EBITDA"))
                .andExpect(jsonPath("$.data.facts[6].value").value(290000.0))
                .andExpect(jsonPath("$.data.facts[7].factCode").value("NET_PROFIT"))
                .andExpect(jsonPath("$.data.facts[7].value").value(200000.0))
                // All 11 FinancialRatioCode ratios are computable from this statement's line items.
                .andExpect(jsonPath("$.data.ratios.length()").value(11))
                .andExpect(jsonPath("$.data.ratios[0].ratioCode").value("CURRENT_RATIO"))
                .andExpect(jsonPath("$.data.ratios[0].category").value("LIQUIDITY"))
                .andExpect(jsonPath("$.data.ratios[0].value").value(2.5))
                .andExpect(jsonPath("$.data.ratios[10].ratioCode").value("DSCR"))
                .andExpect(jsonPath("$.data.ratios[10].value").value(4.1429))
                // No previous period exists yet, so DECLINING_REVENUE/DECLINING_EBITDA are skipped.
                // EnumMap order of the remaining four: NEGATIVE_CASH_FLOW(0), HIGH_LEVERAGE(1),
                // LOW_LIQUIDITY(2), WEAK_INTEREST_COVERAGE(3) — none triggered for this statement.
                .andExpect(jsonPath("$.data.riskIndicators.length()").value(4))
                .andExpect(jsonPath("$.data.riskIndicators[0].indicatorCode").value("NEGATIVE_CASH_FLOW"))
                .andExpect(jsonPath("$.data.riskIndicators[0].triggered").value(false))
                .andExpect(jsonPath("$.data.riskIndicators[1].indicatorCode").value("HIGH_LEVERAGE"))
                .andExpect(jsonPath("$.data.riskIndicators[1].triggered").value(false))
                .andExpect(jsonPath("$.data.riskIndicators[2].indicatorCode").value("LOW_LIQUIDITY"))
                .andExpect(jsonPath("$.data.riskIndicators[2].triggered").value(false))
                .andExpect(jsonPath("$.data.riskIndicators[3].indicatorCode").value("WEAK_INTEREST_COVERAGE"))
                .andExpect(jsonPath("$.data.riskIndicators[3].triggered").value(false));

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements/" + statementId + "/analyze"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/loans/" + reference + "/financial-statements/" + statementId + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    /**
     * DECLINING_REVENUE/DECLINING_EBITDA compare against the immediately preceding period for the
     * same application. The FY2024 statement's revenue (900000) and EBITDA (190000, derived from
     * 900000 - 600000 COGS - 150000 opex + 40000 D&A) are both below FY2023's (1000000 / 290000),
     * so analyzing FY2024 must trigger both.
     */
    @Test
    void decliningRevenueAndEbitdaRiskIndicatorsCompareAgainstThePreviousFinancialPeriod() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        String firstStatementJson = """
                {
                  "period": {
                    "periodLabel": "FY2023",
                    "periodType": "AUDITED",
                    "startDate": "2022-04-01",
                    "endDate": "2023-03-31"
                  },
                  "lineItems": [
                    { "lineItemCode": "REVENUE", "value": 1000000.00 },
                    { "lineItemCode": "COST_OF_GOODS_SOLD", "value": 600000.00 },
                    { "lineItemCode": "OPERATING_EXPENSES", "value": 150000.00 },
                    { "lineItemCode": "DEPRECIATION_AMORTIZATION", "value": 40000.00 },
                    { "lineItemCode": "INTEREST_EXPENSE", "value": 20000.00 },
                    { "lineItemCode": "TAX_EXPENSE", "value": 30000.00 }
                  ]
                }
                """;

        String firstResponseJson = mockMvc.perform(post("/api/loans/" + reference + "/financial-statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstStatementJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstStatementId = firstResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements/" + firstStatementId + "/analyze"))
                .andExpect(status().isOk());

        String secondStatementJson = """
                {
                  "period": {
                    "periodLabel": "FY2024",
                    "periodType": "AUDITED",
                    "startDate": "2023-04-01",
                    "endDate": "2024-03-31"
                  },
                  "lineItems": [
                    { "lineItemCode": "REVENUE", "value": 900000.00 },
                    { "lineItemCode": "COST_OF_GOODS_SOLD", "value": 600000.00 },
                    { "lineItemCode": "OPERATING_EXPENSES", "value": 150000.00 },
                    { "lineItemCode": "DEPRECIATION_AMORTIZATION", "value": 40000.00 },
                    { "lineItemCode": "INTEREST_EXPENSE", "value": 20000.00 },
                    { "lineItemCode": "TAX_EXPENSE", "value": 30000.00 }
                  ]
                }
                """;

        String secondResponseJson = mockMvc.perform(post("/api/loans/" + reference + "/financial-statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondStatementJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondStatementId = secondResponseJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements/" + secondStatementId + "/analyze"))
                .andExpect(status().isOk())
                // EnumMap order: DECLINING_REVENUE(0), DECLINING_EBITDA(1), NEGATIVE_CASH_FLOW(2),
                // WEAK_INTEREST_COVERAGE(3) — HIGH_LEVERAGE/LOW_LIQUIDITY are skipped since this
                // statement carries no balance-sheet line items.
                .andExpect(jsonPath("$.data.riskIndicators.length()").value(4))
                .andExpect(jsonPath("$.data.riskIndicators[0].indicatorCode").value("DECLINING_REVENUE"))
                .andExpect(jsonPath("$.data.riskIndicators[0].triggered").value(true))
                .andExpect(jsonPath("$.data.riskIndicators[1].indicatorCode").value("DECLINING_EBITDA"))
                .andExpect(jsonPath("$.data.riskIndicators[1].triggered").value(true));
    }

    @Test
    void financialStatementWithInvalidPeriodDatesIsRejected() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        String statementJson = """
                {
                  "period": {
                    "periodLabel": "FY2023",
                    "periodType": "PROVISIONAL",
                    "startDate": "2023-03-31",
                    "endDate": "2022-04-01"
                  },
                  "lineItems": [
                    { "lineItemCode": "REVENUE", "value": 1000000.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/loans/" + reference + "/financial-statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statementJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FINANCIAL_PERIOD"));
    }

    @Test
    void unknownFinancialStatementReturnsNotFound() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String customerId = registerCustomer(mockMvc);
        String reference = applyForPersonalLoan(mockMvc, customerId);

        mockMvc.perform(get("/api/loans/" + reference + "/financial-statements/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("FINANCIAL_STATEMENT_NOT_FOUND"));
    }

    private String applyForPersonalLoan(MockMvc mockMvc, String customerId) throws Exception {
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

        return applyResponseJson.split("\"applicationReference\":\"")[1].split("\"")[0];
    }
}
