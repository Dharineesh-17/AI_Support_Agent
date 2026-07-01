package com.example.aiagent.exception;

/**
 * Thrown when the AI model (OpenAI) returns an error, times out,
 * or produces an unparseable structured response.
 *
 * <p>Triggers an HTTP 502 Bad Gateway response via {@link GlobalExceptionHandler},
 * signalling to the client that the upstream AI service is the source of failure.</p>
 */
public class AiServiceException extends RuntimeException {

    /**
     * @param message human-readable description of the AI service failure
     */
    public AiServiceException(String message) {
        super(message);
    }

    /**
     * @param message human-readable description of the AI service failure
     * @param cause   the underlying exception from the Spring AI or HTTP client layer
     */
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
