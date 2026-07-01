package com.example.aiagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Inbound request payload for the ticket analysis endpoint.
 *
 * <p>Clients POST this JSON body to {@code /api/tickets/analyze} to submit
 * a new support ticket for AI-driven classification and resolution generation.</p>
 *
 * @param customerId the ID of the existing customer filing the ticket
 * @param query      the raw natural-language support question from the customer
 */
public record TicketRequest(

    @NotNull(message = "customerId is required")
    @Positive(message = "customerId must be a positive number")
    Long customerId,

    @NotBlank(message = "query must not be blank")
    @Size(min = 10, max = 4000, message = "query must be between 10 and 4000 characters")
    String query

) {}
