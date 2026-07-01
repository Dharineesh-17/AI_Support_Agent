package com.example.aiagent.dto;

import com.example.aiagent.domain.Intent;
import com.example.aiagent.domain.Urgency;

import java.time.LocalDateTime;

/**
 * Outbound response DTO returned after a ticket has been analyzed and persisted.
 *
 * <p>All fields are sourced directly from the saved {@code SupportTicket} entity and
 * the AI {@code TicketAnalysisResponse} — no computation happens in this record.</p>
 *
 * @param ticketId           the auto-generated primary key of the persisted ticket
 * @param customerId         the owning customer's primary key
 * @param customerName       the owning customer's display name
 * @param rawQuery           the original support query text submitted by the customer
 * @param status             the current lifecycle status of the ticket (e.g. RESOLVED, FALLBACK)
 * @param intent             the AI-classified intent category
 * @param urgency            the AI-assessed urgency level
 * @param suggestedResolution the AI-generated resolution text
 * @param createdAt          the timestamp when the ticket was first created
 */
public record TicketResponse(
        Long          ticketId,
        Long          customerId,
        String        customerName,
        String        rawQuery,
        String        status,
        Intent        intent,
        Urgency       urgency,
        String        suggestedResolution,
        LocalDateTime createdAt
) {}
