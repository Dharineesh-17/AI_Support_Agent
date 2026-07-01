package com.example.aiagent.controller;

import com.example.aiagent.dto.TicketRequest;
import com.example.aiagent.dto.TicketResponse;
import com.example.aiagent.service.SupportAgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller exposing the AI customer-support agent endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/tickets/analyze} — synchronous ticket analysis</li>
 *   <li>{@code GET  /api/tickets/stream}  — SSE streaming pipeline</li>
 * </ul>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class SupportAgentController {

    private final SupportAgentService supportAgentService;

    /**
     * Analyzes a customer support ticket synchronously.
     *
     * @param request the ticket payload with customerId and query
     * @return a fully-populated {@link TicketResponse} with AI classifications
     */
    @PostMapping("/analyze")
    public ResponseEntity<TicketResponse> analyzeTicket(@Valid @RequestBody TicketRequest request) {
        log.info("POST /api/tickets/analyze — customerId={}", request.customerId());
        TicketResponse response = supportAgentService.analyzeTicket(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Streams incremental ticket analysis stages as Server-Sent Events.
     *
     * @param customerId the customer's ID (query param)
     * @param query      the support query text (query param)
     * @return an {@link SseEmitter} that broadcasts pipeline progress events
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTicketAnalysis(
            @RequestParam @Positive Long customerId,
            @RequestParam @NotBlank String query) {
        log.info("GET /api/tickets/stream — customerId={}", customerId);
        return supportAgentService.streamTicketAnalysis(customerId, query);
    }
}
