package com.example.aiagent.repository;

import com.example.aiagent.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SupportTicket} entities.
 *
 * <p>Provides standard CRUD operations plus domain-specific queries for
 * retrieving tickets by status, customer, or bulk-updating ticket lifecycle state.</p>
 */
@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    /**
     * Retrieves all tickets belonging to a specific customer, ordered by creation time
     * descending so the most recent ticket appears first.
     *
     * @param customerId the primary key of the owning customer
     * @return a list of tickets, may be empty if the customer has none
     */
    List<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    /**
     * Retrieves all tickets in a given lifecycle status, ordered by creation time ascending
     * (oldest first) — useful for processing work queues FIFO.
     *
     * @param status the lifecycle status to filter on (e.g. "OPEN", "PROCESSING")
     * @return a list of matching tickets
     */
    List<SupportTicket> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Counts the number of open tickets for a specific customer.
     *
     * @param customerId the customer's primary key
     * @param status     the status string to filter by
     * @return the count of matching tickets
     */
    long countByCustomerIdAndStatus(Long customerId, String status);

    /**
     * Bulk-updates the status of a single ticket identified by its primary key.
     * Annotated with {@code @Modifying} so Spring Data JPA flushes and clears
     * the persistence context after the update.
     *
     * @param id     the ticket's primary key
     * @param status the new status string to apply
     */
    @Modifying
    @Query("UPDATE SupportTicket t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * Retrieves the most recently created tickets across all customers,
     * limited to a configurable page size — useful for an admin dashboard.
     *
     * @param limit maximum number of records to return
     * @return list of the most recent tickets
     */
    @Query(value = "SELECT * FROM support_tickets ORDER BY created_at DESC LIMIT :limit",
           nativeQuery = true)
    List<SupportTicket> findMostRecent(@Param("limit") int limit);
}
