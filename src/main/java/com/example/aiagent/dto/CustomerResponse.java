package com.example.aiagent.dto;

/**
 * Response payload returned after creating or fetching a customer.
 *
 * @param id    the customer's auto-generated primary key
 * @param name  the customer's full name
 * @param email the customer's email address
 */
public record CustomerResponse(
        Long   id,
        String name,
        String email
) {}
