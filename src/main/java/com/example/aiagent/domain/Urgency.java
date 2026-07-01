package com.example.aiagent.domain;

/**
 * Represents the severity / time-sensitivity of a customer support ticket.
 *
 * <ul>
 *   <li>{@link #HIGH}   — immediate response required (SLA: 4 hours)</li>
 *   <li>{@link #MEDIUM} — standard response (SLA: 1 business day)</li>
 *   <li>{@link #LOW}    — routine inquiry (SLA: 3 business days)</li>
 * </ul>
 */
public enum Urgency {

    /** Critical issue requiring immediate escalation — SLA 4 hours. */
    HIGH,

    /** Standard priority — SLA 1 business day. */
    MEDIUM,

    /** Low-priority informational query — SLA 3 business days. */
    LOW
}
