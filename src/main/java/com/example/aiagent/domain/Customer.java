package com.example.aiagent.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a customer in the support system.
 *
 * <p>Maps to the {@code customers} table in MySQL. The bidirectional
 * relationship to {@link SupportTicket} is lazily loaded to prevent
 * N+1 query issues in list operations.</p>
 */
@Entity
@Table(
    name = "customers",
    uniqueConstraints = @UniqueConstraint(name = "uq_customer_email", columnNames = "email")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "tickets")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotBlank(message = "Customer name must not be blank")
    @Size(max = 255, message = "Customer name must not exceed 255 characters")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotBlank(message = "Customer email must not be blank")
    @Email(message = "Customer email must be a valid email address")
    @Size(max = 255, message = "Customer email must not exceed 255 characters")
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @OneToMany(
        mappedBy = "customer",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<SupportTicket> tickets = new ArrayList<>();

    /**
     * Convenience helper that keeps the bidirectional relationship consistent.
     *
     * @param ticket the ticket to attach to this customer
     */
    public void addTicket(SupportTicket ticket) {
        tickets.add(ticket);
        ticket.setCustomer(this);
    }

    /**
     * Convenience helper that removes a ticket while keeping both sides in sync.
     *
     * @param ticket the ticket to detach from this customer
     */
    public void removeTicket(SupportTicket ticket) {
        tickets.remove(ticket);
        ticket.setCustomer(null);
    }
}
