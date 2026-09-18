package com.opencredit.platform.customer;

import com.opencredit.platform.customer.dto.CustomerRequest;
import com.opencredit.platform.customer.dto.CustomerResponse;
import com.opencredit.platform.customer.exception.CustomerNotFoundException;
import com.opencredit.platform.customer.exception.DuplicateCustomerException;
import com.opencredit.platform.customer.model.Customer;
import com.opencredit.platform.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;

    private CustomerRequest request() {
        CustomerRequest request = new CustomerRequest();
        request.setFullName("Piyush Prasad");
        request.setEmail("piyush@example.com");
        request.setPhoneNumber("9876543210");
        request.setDateOfBirth(LocalDate.of(1995, 5, 15));
        request.setPanNumber("ABCDE1234F");
        return request;
    }

    @Test
    void registersAndReturnsCustomer() {
        lenient().when(repository.existsByEmail(any())).thenReturn(false);
        lenient().when(repository.existsByPhoneNumber(any())).thenReturn(false);
        lenient().when(repository.existsByPanNumber(any())).thenReturn(false);

        CustomerResponse response = new CustomerService(repository).register(request());

        assertThat(response.getFullName()).isEqualTo("Piyush Prasad");
        assertThat(response.getEmail()).isEqualTo("piyush@example.com");
        assertThat(response.getId()).isNotNull();
        verify(repository).save(any(Customer.class));
    }

    @Test
    void rejectsDuplicateEmail() {
        when(repository.existsByEmail("piyush@example.com")).thenReturn(true);

        assertThatThrownBy(() -> new CustomerService(repository).register(request()))
                .isInstanceOf(DuplicateCustomerException.class);
    }

    @Test
    void rejectsDuplicatePhoneNumber() {
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.existsByPhoneNumber("9876543210")).thenReturn(true);

        assertThatThrownBy(() -> new CustomerService(repository).register(request()))
                .isInstanceOf(DuplicateCustomerException.class);
    }

    @Test
    void rejectsDuplicatePan() {
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.existsByPhoneNumber(any())).thenReturn(false);
        when(repository.existsByPanNumber("ABCDE1234F")).thenReturn(true);

        assertThatThrownBy(() -> new CustomerService(repository).register(request()))
                .isInstanceOf(DuplicateCustomerException.class);
    }

    @Test
    void throwsWhenCustomerNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CustomerService(repository).getEntity(id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void findByIdReturnsMappedResponse() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(id)
                .fullName("Piyush Prasad")
                .email("piyush@example.com")
                .phoneNumber("9876543210")
                .dateOfBirth(LocalDate.of(1995, 5, 15))
                .panNumber("ABCDE1234F")
                .createdAt(Instant.now())
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = new CustomerService(repository).findById(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getPanNumber()).isEqualTo("ABCDE1234F");
    }
}
