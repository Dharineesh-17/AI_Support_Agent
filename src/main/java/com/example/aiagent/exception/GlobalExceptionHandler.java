package com.example.aiagent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler providing RFC 7807 Problem Details responses.
 *
 * <p>Handles validation errors, resource-not-found, AI service failures,
 * and unexpected runtime exceptions in a consistent JSON format consumable
 * by the frontend EventSource error handler.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Jakarta Bean Validation failures (400 Bad Request).
     * Returns a map of field names → validation messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more request fields failed validation");
        problem.setType(URI.create("https://api.example.com/errors/validation-failed"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("fieldErrors", fieldErrors);

        log.warn("Validation error on request: {}", fieldErrors);
        return problem;
    }

    /**
     * Handles resource-not-found scenarios (404 Not Found).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Resource Not Found");
        problem.setDetail(ex.getMessage());
        problem.setType(URI.create("https://api.example.com/errors/resource-not-found"));
        problem.setProperty("timestamp", Instant.now());

        log.warn("Resource not found: {}", ex.getMessage());
        return problem;
    }

    /**
     * Handles AI service failures (502 Bad Gateway) — e.g. OpenAI API errors.
     */
    @ExceptionHandler(AiServiceException.class)
    public ProblemDetail handleAiServiceError(AiServiceException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        problem.setTitle("AI Service Unavailable");
        problem.setDetail(ex.getMessage());
        problem.setType(URI.create("https://api.example.com/errors/ai-service-error"));
        problem.setProperty("timestamp", Instant.now());

        log.error("AI service error: {}", ex.getMessage(), ex);
        return problem;
    }

    /**
     * Catch-all handler for unexpected runtime exceptions (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        problem.setType(URI.create("https://api.example.com/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now());

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return problem;
    }
}
