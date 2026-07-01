package com.example.aiagent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable structured output record that the Spring AI {@code BeanOutputConverter}
 * maps the language model's JSON response payload into.
 *
 * <p>The record field names are annotated with {@link JsonProperty} to guarantee
 * correct Jackson deserialization even when using non-standard naming conventions
 * or obfuscation.</p>
 *
 * <p>Example JSON the model is instructed to produce:
 * <pre>{@code
 * {
 *   "intent": "REFUND",
 *   "urgency": "HIGH",
 *   "suggestedResolution": "Process a full refund within 24 hours per our 30-day policy..."
 * }
 * }</pre>
 *
 * @param intent              the classified intent category of the ticket
 * @param urgency             the assessed urgency / severity level
 * @param suggestedResolution a human-readable resolution drafted by the AI agent
 */
public record TicketAnalysisResponse(

    @JsonProperty("intent")
    Intent intent,

    @JsonProperty("urgency")
    Urgency urgency,

    @JsonProperty("suggestedResolution")
    String suggestedResolution

) {}
