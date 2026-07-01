package com.example.aiagent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource (Customer, Ticket) cannot be found in the database.
 *
 * <p>Annotated with {@code @ResponseStatus(NOT_FOUND)} so Spring MVC automatically
 * maps it to an HTTP 404 response when thrown from a controller, without requiring
 * explicit exception handler mapping.</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param resourceType the type of resource that was not found (e.g. "Customer")
     * @param id           the identifier that was looked up
     */
    public ResourceNotFoundException(String resourceType, Object id) {
        super(String.format("%s with id [%s] was not found", resourceType, id));
    }

    /**
     * @param message a fully-formed descriptive message
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
