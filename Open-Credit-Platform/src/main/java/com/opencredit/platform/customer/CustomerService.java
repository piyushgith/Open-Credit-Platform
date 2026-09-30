package com.opencredit.platform.customer;

import com.opencredit.platform.customer.dto.CustomerRequest;
import com.opencredit.platform.customer.dto.CustomerResponse;
import com.opencredit.platform.customer.exception.CustomerNotFoundException;
import com.opencredit.platform.customer.exception.DuplicateCustomerException;
import com.opencredit.platform.customer.model.Customer;
import com.opencredit.platform.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public CustomerResponse register(CustomerRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(email)) {
            throw new DuplicateCustomerException("email", email);
        }
        if (repository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateCustomerException("phoneNumber", request.getPhoneNumber());
        }
        if (repository.existsByPanNumber(request.getPanNumber())) {
            throw new DuplicateCustomerException("panNumber", request.getPanNumber());
        }

        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .fullName(request.getFullName())
                .email(email)
                .phoneNumber(request.getPhoneNumber())
                .dateOfBirth(request.getDateOfBirth())
                .panNumber(request.getPanNumber())
                .createdAt(Instant.now())
                .build();

        repository.save(customer);
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Customer getEntity(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .dateOfBirth(customer.getDateOfBirth())
                .panNumber(maskPan(customer.getPanNumber()))
                .createdAt(customer.getCreatedAt())
                .build();
    }

    /** Shows only the last 4 characters of the PAN, e.g. {@code ******234F}. */
    private static String maskPan(String panNumber) {
        return "*".repeat(panNumber.length() - 4) + panNumber.substring(panNumber.length() - 4);
    }
}
