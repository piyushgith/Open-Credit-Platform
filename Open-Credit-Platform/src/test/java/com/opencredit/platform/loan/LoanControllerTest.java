package com.opencredit.platform.loan;

import com.opencredit.platform.loan.dto.PersonalLoanResponse;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.model.DecisionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
class LoanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanApplicationService loanApplicationService;

    private static final String PERSONAL_REQUEST_JSON = """
            {
              "productType": "PERSONAL",
              "applicantName": "Piyush Prasad",
              "requestedAmount": 500000.00,
              "tenureMonths": 60,
              "monthlyIncome": 120000.00,
              "existingEmi": 15000.00,
              "employmentType": "SALARIED"
            }
            """;

    @Test
    void applyReturnsCreatedWithSuccessEnvelope() throws Exception {
        PersonalLoanResponse response = new PersonalLoanResponse();
        response.setApplicationReference("LN-00000001");
        response.setDecision(DecisionStatus.APPROVED);
        response.setApprovedAmount(new BigDecimal("500000.00"));
        response.setInterestRate(new BigDecimal("11.5"));
        response.setMonthlyEmi(new BigDecimal("11000.00"));
        response.setTenureMonths(60);
        response.setFoir(new BigDecimal("0.2167"));
        when(loanApplicationService.apply(any())).thenReturn(response);

        mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PERSONAL_REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/loans/LN-00000001"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.productType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.applicationReference").value("LN-00000001"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void applyReturnsUnprocessableEntityWithFieldErrorsOnValidationFailure() throws Exception {
        String invalidJson = """
                {
                  "productType": "PERSONAL",
                  "applicantName": "",
                  "requestedAmount": -5,
                  "tenureMonths": 2
                }
                """;

        mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].fieldErrors.applicantName").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void applyReturnsBadRequestForUnknownProductType() throws Exception {
        String unknownProductJson = """
                {
                  "productType": "HOME",
                  "applicantName": "X",
                  "requestedAmount": 100000,
                  "tenureMonths": 12
                }
                """;

        mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownProductJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_PRODUCT_TYPE"));
    }

    @Test
    void applyReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/loans/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getByReferenceReturnsNotFoundWhenMissing() throws Exception {
        when(loanApplicationService.findByReference("LN-99999999"))
                .thenThrow(new LoanApplicationNotFoundException("LN-99999999"));

        mockMvc.perform(get("/api/loans/LN-99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("LOAN_APPLICATION_NOT_FOUND"));
    }
}
