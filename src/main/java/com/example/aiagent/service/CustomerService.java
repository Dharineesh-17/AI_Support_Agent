package com.example.aiagent.service;

import com.example.aiagent.domain.Customer;
import com.example.aiagent.dto.CustomerRequest;
import com.example.aiagent.dto.CustomerResponse;
import com.example.aiagent.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing live customer registration and lookup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Registers a new customer. If the email already exists, returns the existing customer
     * instead of throwing an error — idempotent behaviour for the UI.
     *
     * @param request the registration payload
     * @return the created or existing customer as a response record
     */
    @Transactional
    public CustomerResponse registerOrFetch(CustomerRequest request) {
        // If email already registered, just return that customer (idempotent)
        return customerRepository.findByEmail(request.email())
                .map(existing -> {
                    log.info("Customer already exists with email={} — returning existing id={}", request.email(), existing.getId());
                    return toResponse(existing);
                })
                .orElseGet(() -> {
                    Customer customer = Customer.builder()
                            .name(request.name())
                            .email(request.email())
                            .build();
                    Customer saved = customerRepository.save(customer);
                    log.info("Registered new customer id={} name='{}' email='{}'", saved.getId(), saved.getName(), saved.getEmail());
                    return toResponse(saved);
                });
    }

    /**
     * Returns all registered customers, ordered by ID ascending.
     *
     * @return list of all customer response records
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ─── Mapper ──────────────────────────────────────────────────────────────

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail());
    }
}
