package com.example.aiagent.domain;

/**
 * Companion enums and structured output record for ticket AI analysis.
 *
 * <p>{@link TicketAnalysisResponse} is the canonical data type that the Spring AI
 * {@code BeanOutputConverter} will deserialize the language model's JSON payload into.
 * The record's field names must exactly match the JSON property names produced by the
 * model prompt's format instructions.</p>
 *
 * <p><strong>Intent enum</strong> classifies the customer's primary need:
 * <ul>
 *   <li>{@link Intent#REFUND} — customer is requesting money back or a return</li>
 *   <li>{@link Intent#TECH_SUPPORT} — customer is experiencing a technical issue</li>
 *   <li>{@link Intent#GENERAL} — general inquiry, account management, or other</li>
 * </ul>
 *
 * <p><strong>Urgency enum</strong> describes the severity of the ticket:
 * <ul>
 *   <li>{@link Urgency#HIGH} — immediate attention needed (outages, financial loss)</li>
 *   <li>{@link Urgency#MEDIUM} — should be addressed within the business day</li>
 *   <li>{@link Urgency#LOW} — routine inquiry with no time pressure</li>
 * </ul>
 */

// ─────────────────────────────────────────────────────────────────────────────
// Intent Enum
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents the primary purpose / category of the customer support request.
 */
public enum Intent {

    /** Customer is requesting a refund, return, or compensation. */
    REFUND,

    /** Customer is facing a technical problem, bug, or integration issue. */
    TECH_SUPPORT,

    /** General inquiry, account management, shipping, or any other topic. */
    GENERAL
}
