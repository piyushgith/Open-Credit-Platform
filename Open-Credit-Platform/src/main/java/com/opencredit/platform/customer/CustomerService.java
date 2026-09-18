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
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public CustomerResponse register(CustomerRequest request) {
        if (repository.existsByEmail(request.getEmail())) {
            throw new DuplicateCustomerException("email", request.getEmail());
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
                .email(request.getEmail())
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
                .panNumber(customer.getPanNumber())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
