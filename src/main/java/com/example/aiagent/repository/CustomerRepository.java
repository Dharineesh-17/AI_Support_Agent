package com.example.aiagent.repository;

import com.example.aiagent.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Customer} entities.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository}
 * plus domain-specific finder methods for customer lookup by email.</p>
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Finds a customer by their unique email address.
     *
     * @param email the email to look up (case-sensitive, matches DB collation)
     * @return an {@link Optional} containing the customer if found
     */
    Optional<Customer> findByEmail(String email);

    /**
     * Checks whether a customer with the given email already exists.
     *
     * @param email the email address to check
     * @return {@code true} if a customer record with this email exists
     */
    boolean existsByEmail(String email);

    /**
     * Fetches a customer together with their tickets in a single JOIN FETCH query,
     * avoiding the N+1 problem when both sides of the relationship are needed.
     *
     * @param id the primary key of the customer
     * @return an {@link Optional} containing the customer with initialized ticket collection
     */
    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.tickets WHERE c.id = :id")
    Optional<Customer> findByIdWithTickets(@Param("id") Long id);
}
