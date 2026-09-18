package com.opencredit.platform.customer;

import com.opencredit.platform.customer.dto.CustomerResponse;
import com.opencredit.platform.customer.exception.CustomerNotFoundException;
import com.opencredit.platform.customer.exception.DuplicateCustomerException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    private static final String VALID_REQUEST_JSON = """
            {
              "fullName": "Piyush Prasad",
              "email": "piyush@example.com",
              "phoneNumber": "9876543210",
              "dateOfBirth": "1995-05-15",
              "panNumber": "ABCDE1234F"
            }
            """;

    @Test
    void registerReturnsCreatedWithSuccessEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerResponse response = CustomerResponse.builder()
                .id(id)
                .fullName("Piyush Prasad")
                .email("piyush@example.com")
                .phoneNumber("9876543210")
                .dateOfBirth(LocalDate.of(1995, 5, 15))
                .panNumber("ABCDE1234F")
                .createdAt(Instant.now())
                .build();
        when(customerService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/customers/" + id))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value("piyush@example.com"));
    }

    @Test
    void registerReturnsUnprocessableEntityOnInvalidPan() throws Exception {
        String invalidJson = """
                {
                  "fullName": "Piyush Prasad",
                  "email": "piyush@example.com",
                  "phoneNumber": "9876543210",
                  "dateOfBirth": "1995-05-15",
                  "panNumber": "invalid"
                }
                """;

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].fieldErrors.panNumber").exists());
    }

    @Test
    void registerReturnsConflictOnDuplicateEmail() throws Exception {
        when(customerService.register(any()))
                .thenThrow(new DuplicateCustomerException("email", "piyush@example.com"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_CUSTOMER"));
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.findById(eq(id))).thenThrow(new CustomerNotFoundException(id));

        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("CUSTOMER_NOT_FOUND"));
    }
}
