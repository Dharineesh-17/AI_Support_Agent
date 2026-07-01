package com.example.aiagent.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity representing a customer support ticket.
 *
 * <p>Persists the raw customer query alongside AI-derived classifications
 * (intent, urgency) and the generated resolution. The {@code status} field
 * follows a simple lifecycle: OPEN → PROCESSING → RESOLVED / FAILED.</p>
 */
@Entity
@Table(
    name = "support_tickets",
    indexes = {
        @Index(name = "idx_ticket_customer",  columnList = "customer_id"),
        @Index(name = "idx_ticket_status",    columnList = "status"),
        @Index(name = "idx_ticket_intent",    columnList = "intent"),
        @Index(name = "idx_ticket_urgency",   columnList = "urgency"),
        @Index(name = "idx_ticket_created",   columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "customer")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull(message = "Customer association is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ticket_customer"))
    private Customer customer;

    @NotBlank(message = "Raw query must not be blank")
    @Column(name = "raw_query", nullable = false, columnDefinition = "TEXT")
    private String rawQuery;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "intent", length = 50)
    private String intent;

    @Column(name = "urgency", length = 50)
    private String urgency;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
