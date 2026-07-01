package com.example.aiagent.controller;

import com.example.aiagent.dto.CustomerRequest;
import com.example.aiagent.dto.CustomerResponse;
import com.example.aiagent.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for live customer registration and lookup.
 *
 * <ul>
 *   <li>{@code POST /api/customers}      — register a new customer</li>
 *   <li>{@code GET  /api/customers}      — list all registered customers</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Registers a new customer (or returns existing if email already taken).
     *
     * @param request the customer's name and email
     * @return 201 Created with the customer record
     */
    @PostMapping
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CustomerRequest request) {
        log.info("POST /api/customers — email={}", request.email());
        CustomerResponse response = customerService.registerOrFetch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all registered customers.
     *
     * @return 200 OK with list of all customers
     */
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> listAll() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }
}
