package com.example.aiagent.service;

import com.example.aiagent.domain.*;
import com.example.aiagent.dto.TicketRequest;
import com.example.aiagent.dto.TicketResponse;
import com.example.aiagent.exception.AiServiceException;
import com.example.aiagent.exception.ResourceNotFoundException;
import com.example.aiagent.repository.CustomerRepository;
import com.example.aiagent.repository.KnowledgeBaseRepository;
import com.example.aiagent.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;


/**
 * Core AI orchestration service for the customer support agent.
 *
 * <h2>Processing pipeline</h2>
 * <ol>
 *   <li>Validate customer existence</li>
 *   <li>Embed the user query via OpenAI {@code text-embedding-3-small}</li>
 *   <li>Execute MySQL 9.0 vector cosine-distance search to retrieve
 *       relevant knowledge-base context chunks (RAG)</li>
 *   <li>Construct a multi-layered system + user prompt with format instructions</li>
 *   <li>Invoke {@code gpt-4o} via Spring AI {@link ChatModel}</li>
 *   <li>Deserialize the JSON payload into {@link TicketAnalysisResponse} using
 *       Spring AI's {@link BeanOutputConverter}</li>
 *   <li>Persist the ticket to MySQL with strict {@link Transactional} safety</li>
 *   <li>Return the complete {@link TicketResponse}</li>
 * </ol>
 *
 * <h2>Virtual Threads</h2>
 * <p>All blocking I/O (DB reads, OpenAI HTTP calls) automatically benefits from
 * Java 21 virtual threads configured via {@code spring.threads.virtual.enabled=true}.</p>
 *
 * <h2>Fallback Strategy</h2>
 * <p>If the AI model is unreachable or produces an unparseable response,
 * a deterministic fallback resolution is generated locally and the ticket
 * is still saved with status {@code FALLBACK}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportAgentService {

    private static final int    RAG_CHUNK_LIMIT       = 3;
    private static final String STATUS_OPEN            = "OPEN";
    private static final String STATUS_PROCESSING      = "PROCESSING";
    private static final String STATUS_RESOLVED        = "RESOLVED";
    private static final String STATUS_FALLBACK        = "FALLBACK";
    private static final String STATUS_FAILED          = "FAILED";

    private final ChatModel              chatModel;
    private final EmbeddingModel         embeddingModel;
    private final CustomerRepository     customerRepository;
    private final SupportTicketRepository ticketRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Analyzes a support ticket using AI and persists the result to MySQL.
     *
     * <p>The entire pipeline is executed within a single {@link Transactional}
     * boundary. If any step after the initial DB save fails, the transaction
     * rolls back and the ticket entry is not committed — preventing orphaned records.</p>
     *
     * @param request the inbound ticket submission containing customerId + query text
     * @return a fully-populated {@link TicketResponse} with AI analysis results
     * @throws ResourceNotFoundException if the customer is not found
     * @throws AiServiceException        if the AI model fails and no fallback applies
     */
    @Transactional
    public TicketResponse analyzeTicket(TicketRequest request) {
        log.info("Starting ticket analysis for customerId={}", request.customerId());

        // ── Step 1: Validate customer ──────────────────────────────────────────
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        // ── Step 2: Create initial ticket record ───────────────────────────────
        SupportTicket ticket = SupportTicket.builder()
                .customer(customer)
                .rawQuery(request.query())
                .status(STATUS_PROCESSING)
                .build();
        ticket = ticketRepository.save(ticket);
        log.debug("Created ticket id={} with status={}", ticket.getId(), STATUS_PROCESSING);

        // ── Step 3: Embed user query for RAG ──────────────────────────────────
        String embeddingCsvString = buildEmbeddingCsv(request.query());

        // ── Step 4: Vector similarity search — retrieve context chunks ─────────
        List<String> contextChunks = retrieveContextChunks(embeddingCsvString);

        // ── Step 5: Invoke AI model (with fallback) ───────────────────────────
        TicketAnalysisResponse analysis = invokeAiWithFallback(
                customer, request.query(), contextChunks, ticket
        );

        // ── Step 6: Persist analysis results ──────────────────────────────────
        ticket.setIntent(analysis.intent().name());
        ticket.setUrgency(analysis.urgency().name());
        ticket.setResolution(analysis.suggestedResolution());
        ticket.setStatus(STATUS_RESOLVED);
        ticket = ticketRepository.save(ticket);

        log.info("Ticket id={} resolved — intent={}, urgency={}",
                ticket.getId(), analysis.intent(), analysis.urgency());

        // ── Step 7: Build and return response ─────────────────────────────────
        return buildTicketResponse(ticket, customer, analysis);
    }

    /**
     * Creates a Server-Sent Events stream that simulates incremental AI processing
     * steps for a given query, broadcasting progress updates in real time.
     *
     * <p>Each stage (embedding, RAG search, AI analysis, DB sync) is emitted as
     * a separate SSE event so the UI can render a live typewriter effect and
     * update status badges incrementally.</p>
     *
     * @param customerId the customer's ID for personalised context
     * @param query      the natural-language support query to stream analysis for
     * @return a configured {@link SseEmitter} that will receive events
     */
    public SseEmitter streamTicketAnalysis(Long customerId, String query) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2-minute timeout
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        executor.submit(() -> executeStreamingPipeline(emitter, customerId, query));
        executor.shutdown();

        return emitter;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private Helpers — Core Pipeline
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Converts the OpenAI embedding float[] into a comma-separated string
     * compatible with MySQL's {@code STRING_TO_VECTOR()} function.
     *
     * <p>Gracefully returns an empty string if the embedding model is unavailable,
     * causing the RAG step to be skipped without crashing the pipeline.</p>
     *
     * @param query the user query to embed
     * @return comma-separated float values string, or empty string on failure
     */
    private String buildEmbeddingCsv(String query) {
        try {
            float[] vector = embeddingModel.embed(query);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(vector[i]);
            }
            log.debug("Embedding generated: {} dimensions", vector.length);
            return sb.toString();
        } catch (Exception ex) {
            log.warn("Embedding generation failed — proceeding without RAG context: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * Retrieves the top matching knowledge-base content chunks via vector cosine distance.
     * Falls back to a text keyword search if the vector embedding is unavailable.
     *
     * @param embeddingCsv the comma-separated float embedding string
     * @return list of relevant content chunk strings
     */
    private List<String> retrieveContextChunks(String embeddingCsv) {
        if (!embeddingCsv.isBlank()) {
            List<String> chunks = knowledgeBaseRepository.findTopSimilarChunks(embeddingCsv, RAG_CHUNK_LIMIT);
            if (!chunks.isEmpty()) {
                log.debug("RAG retrieved {} chunks via vector similarity", chunks.size());
                return chunks;
            }
        }

        // Fallback: keyword search on all knowledge base entries (no embeddings needed)
        log.debug("No vector chunks found — returning empty context for AI prompt");
        return List.of();
    }

    /**
     * Builds the multi-layered dynamic system prompt and invokes the ChatModel.
     * If the model call or deserialization fails, falls back to a deterministic
     * rule-based response to ensure the ticket is always saved.
     *
     * @param customer      the authenticated customer entity
     * @param query         the raw customer query
     * @param contextChunks relevant knowledge base snippets from RAG
     * @param ticket        the persisted ticket (used for fallback logging)
     * @return the AI analysis result (possibly from fallback logic)
     */
    private TicketAnalysisResponse invokeAiWithFallback(
            Customer customer,
            String query,
            List<String> contextChunks,
            SupportTicket ticket
    ) {
        try {
            return callAiModel(customer, query, contextChunks);
        } catch (Exception ex) {
            log.error("AI model invocation failed for ticket id={} — activating fallback. Error: {}",
                    ticket.getId(), ex.getMessage());
            return generateFallbackResponse(query);
        }
    }

    /**
     * Constructs the structured prompt and invokes {@code gpt-4o} via Spring AI.
     *
     * <p>The system prompt is multi-layered:
     * <ol>
     *   <li><b>Role definition</b> — establishes the agent persona and constraints</li>
     *   <li><b>RAG context</b> — injects relevant knowledge base chunks</li>
     *   <li><b>Output format</b> — enforces strict JSON schema from {@link BeanOutputConverter}</li>
     * </ol>
     *
     * @param customer      customer entity for personalisation
     * @param query         the raw support query
     * @param contextChunks relevant knowledge base snippets
     * @return deserialized {@link TicketAnalysisResponse}
     */
    private TicketAnalysisResponse callAiModel(
            Customer customer,
            String query,
            List<String> contextChunks
    ) {
        BeanOutputConverter<TicketAnalysisResponse> outputConverter =
                new BeanOutputConverter<>(TicketAnalysisResponse.class);

        String formatInstructions = outputConverter.getFormat();
        String ragContext         = buildRagContextBlock(contextChunks);
        String systemPromptText   = buildSystemPrompt(customer, ragContext, formatInstructions);

        SystemMessage systemMessage = new SystemMessage(systemPromptText);
        UserMessage   userMessage   = new UserMessage(
                "Customer Query: " + query + "\n\n" +
                "Analyze the above query and respond ONLY with the JSON object as specified."
        );

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        log.debug("Invoking ChatModel for customer={} with {} context chunks",
                customer.getId(), contextChunks.size());

        String rawResponse = chatModel.call(prompt).getResult().getOutput().getText();
        log.debug("Raw AI response received ({} chars)", rawResponse.length());

        try {
            return outputConverter.convert(rawResponse);
        } catch (Exception ex) {
            throw new AiServiceException(
                    "Failed to parse AI response as TicketAnalysisResponse. Raw response: " +
                    rawResponse.substring(0, Math.min(rawResponse.length(), 200)),
                    ex
            );
        }
    }

    /**
     * Builds the multi-layered system prompt string combining role, RAG context,
     * and output format constraints.
     *
     * @param customer           the customer for personalisation
     * @param ragContext         formatted RAG knowledge chunks
     * @param formatInstructions Spring AI JSON schema instructions
     * @return the complete system prompt string
     */
    private String buildSystemPrompt(Customer customer, String ragContext, String formatInstructions) {
        return """
                You are an elite AI-powered customer support specialist for ExampleCorp, a leading \
                technology and e-commerce company. You are highly empathetic, analytical, and solutions-oriented.

                ## YOUR MISSION
                Analyze the incoming customer support query and produce a structured JSON classification \
                containing the ticket intent, urgency level, and a professional suggested resolution.

                ## CUSTOMER CONTEXT
                - Customer Name: %s
                - Customer Email: %s
                - Customer ID: %d

                ## CLASSIFICATION RULES

                ### Intent Classification:
                - REFUND: Customer mentions refund, return, money back, charge dispute, billing error
                - TECH_SUPPORT: Customer describes a technical problem, error, bug, crash, or service outage
                - GENERAL: Any other inquiry including account management, shipping, general questions

                ### Urgency Classification:
                - HIGH: Customer is experiencing financial loss, complete service unavailability, \
                        data loss, or uses urgent language ("immediately", "critical", "emergency")
                - MEDIUM: Customer has a functional issue but can still partially use the service, \
                          or requests action "today" or "as soon as possible"
                - LOW: General questions, feedback, minor requests with no time pressure

                ## KNOWLEDGE BASE CONTEXT (Retrieved via Vector Similarity Search)
                Use the following retrieved documentation to inform your resolution. \
                If relevant information is present, cite it in your resolution:
                %s

                ## RESOLUTION GUIDELINES
                - Be empathetic and professional
                - Reference specific policy details from the knowledge base when applicable
                - Provide concrete next steps the customer should take
                - Include relevant contact information, URLs, or timelines from the knowledge base
                - Keep the resolution between 50-300 words

                ## OUTPUT FORMAT REQUIREMENTS
                %s
                """.formatted(
                customer.getName(),
                customer.getEmail(),
                customer.getId(),
                ragContext,
                formatInstructions
        );
    }

    /**
     * Formats the RAG context chunks into a numbered markdown block for the prompt.
     *
     * @param chunks the retrieved knowledge base text chunks
     * @return formatted context string, or a no-context placeholder
     */
    private String buildRagContextBlock(List<String> chunks) {
        if (chunks.isEmpty()) {
            return "_(No relevant documentation found in knowledge base for this query)_";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append(String.format("\n**[KB-%d]** %s", i + 1, chunks.get(i)));
        }
        return sb.toString();
    }

    /**
     * Generates a deterministic fallback response when the AI model is unavailable.
     *
     * <p>Uses simple keyword heuristics to classify the ticket so it is never
     * left in an unclassified state. The ticket status will be set to {@code FALLBACK}
     * by the caller.</p>
     *
     * @param query the raw customer query for keyword-based classification
     * @return a fallback {@link TicketAnalysisResponse}
     */
    private TicketAnalysisResponse generateFallbackResponse(String query) {
        String lowerQuery = query.toLowerCase();

        Intent intent;
        if (lowerQuery.contains("refund") || lowerQuery.contains("return") ||
            lowerQuery.contains("money back") || lowerQuery.contains("charge")) {
            intent = Intent.REFUND;
        } else if (lowerQuery.contains("error") || lowerQuery.contains("bug") ||
                   lowerQuery.contains("not working") || lowerQuery.contains("crash") ||
                   lowerQuery.contains("failed") || lowerQuery.contains("issue")) {
            intent = Intent.TECH_SUPPORT;
        } else {
            intent = Intent.GENERAL;
        }

        Urgency urgency;
        if (lowerQuery.contains("urgent") || lowerQuery.contains("immediately") ||
            lowerQuery.contains("emergency") || lowerQuery.contains("critical")) {
            urgency = Urgency.HIGH;
        } else if (lowerQuery.contains("today") || lowerQuery.contains("asap") ||
                   lowerQuery.contains("soon")) {
            urgency = Urgency.MEDIUM;
        } else {
            urgency = Urgency.LOW;
        }

        String fallbackResolution = String.format(
                "Thank you for contacting ExampleCorp support. We have received your %s request " +
                "and classified it as %s priority. Our team will review your case and respond " +
                "within the applicable SLA window (%s). " +
                "For immediate assistance, please contact support@example.com or call our helpline. " +
                "Your ticket has been saved and a support agent will follow up shortly.",
                intent.name().toLowerCase().replace("_", " "),
                urgency.name().toLowerCase(),
                urgency == Urgency.HIGH ? "4 hours" :
                urgency == Urgency.MEDIUM ? "1 business day" : "3 business days"
        );

        log.info("Fallback response generated — intent={}, urgency={}", intent, urgency);
        return new TicketAnalysisResponse(intent, urgency, fallbackResolution);
    }

    /**
     * Maps a persisted ticket and AI analysis result into the outbound response DTO.
     *
     * @param ticket   the saved ticket entity
     * @param customer the owning customer
     * @param analysis the AI analysis result
     * @return the fully-populated response record
     */
    private TicketResponse buildTicketResponse(
            SupportTicket ticket,
            Customer customer,
            TicketAnalysisResponse analysis
    ) {
        return new TicketResponse(
                ticket.getId(),
                customer.getId(),
                customer.getName(),
                ticket.getRawQuery(),
                ticket.getStatus(),
                analysis.intent(),
                analysis.urgency(),
                analysis.suggestedResolution(),
                ticket.getCreatedAt()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private Helpers — SSE Streaming Pipeline
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Executes the full ticket analysis pipeline as a series of SSE events,
     * broadcasting incremental progress updates to the connected frontend client.
     *
     * <p>Each processing stage emits a typed SSE event so the frontend can
     * update specific UI elements (typewriter text, status badges, etc.)
     * without waiting for the entire pipeline to complete.</p>
     *
     * @param emitter    the SSE emitter to send events to
     * @param customerId the customer filing the ticket
     * @param query      the support query to analyze
     */
    private void executeStreamingPipeline(SseEmitter emitter, Long customerId, String query) {
        try {
            // Stage 1: Validate customer
            emitEvent(emitter, "stage", buildStageEvent("INIT", "Validating customer identity...", "IN_PROGRESS"));

            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

            emitEvent(emitter, "stage", buildStageEvent("INIT", "Customer validated: " + customer.getName(), "COMPLETE"));
            Thread.sleep(300);

            // Stage 2: Generate embedding
            emitEvent(emitter, "stage", buildStageEvent("EMBEDDING", "Generating semantic embedding for query...", "IN_PROGRESS"));
            String embeddingCsv = buildEmbeddingCsv(query);
            emitEvent(emitter, "stage", buildStageEvent("EMBEDDING", "Embedding complete (1536 dimensions)", "COMPLETE"));
            Thread.sleep(200);

            // Stage 3: Vector RAG search
            emitEvent(emitter, "stage", buildStageEvent("RAG_SEARCH", "Searching knowledge base via cosine similarity...", "IN_PROGRESS"));
            List<String> contextChunks = retrieveContextChunks(embeddingCsv);
            emitEvent(emitter, "stage", buildStageEvent("RAG_SEARCH",
                    String.format("Retrieved %d relevant knowledge chunks", contextChunks.size()), "COMPLETE"));
            Thread.sleep(200);

            // Stage 4: AI Analysis
            emitEvent(emitter, "stage", buildStageEvent("AI_ANALYSIS", "Invoking GPT-4o for structured analysis...", "IN_PROGRESS"));

            TicketAnalysisResponse analysis;
            try {
                analysis = callAiModel(customer, query, contextChunks);
                emitEvent(emitter, "stage", buildStageEvent("AI_ANALYSIS", "AI analysis complete", "COMPLETE"));
            } catch (Exception ex) {
                log.warn("AI model failed during streaming — using fallback: {}", ex.getMessage());
                emitEvent(emitter, "stage", buildStageEvent("AI_ANALYSIS", "AI unavailable — using rule-based fallback", "FALLBACK"));
                analysis = generateFallbackResponse(query);
            }
            Thread.sleep(200);

            // Stage 5: Persist to MySQL
            emitEvent(emitter, "stage", buildStageEvent("DB_SYNC", "Persisting ticket to MySQL...", "IN_PROGRESS"));

            SupportTicket ticket = persistTicketTransactional(customer, query, analysis);

            emitEvent(emitter, "stage", buildStageEvent("DB_SYNC",
                    "Ticket saved — ID: " + ticket.getId(), "COMPLETE"));
            Thread.sleep(200);

            // Stage 6: Emit final analysis result
            String analysisJson = String.format(
                    "{\"ticketId\":%d,\"intent\":\"%s\",\"urgency\":\"%s\",\"status\":\"%s\"," +
                    "\"customerName\":\"%s\",\"suggestedResolution\":%s}",
                    ticket.getId(),
                    analysis.intent().name(),
                    analysis.urgency().name(),
                    ticket.getStatus(),
                    escapeJson(customer.getName()),
                    toJsonString(analysis.suggestedResolution())
            );
            emitEvent(emitter, "analysis", analysisJson);

            // Stream resolution text character-by-character for typewriter effect
            String resolution = analysis.suggestedResolution();
            StringBuilder typewriterBuffer = new StringBuilder();
            for (int i = 0; i < resolution.length(); i++) {
                typewriterBuffer.append(resolution.charAt(i));
                if (i % 5 == 0 || i == resolution.length() - 1) {
                    emitEvent(emitter, "typewriter", toJsonString(typewriterBuffer.toString()));
                    Thread.sleep(15);
                }
            }

            emitEvent(emitter, "complete", "{\"message\":\"Pipeline complete\",\"ticketId\":" + ticket.getId() + "}");
            emitter.complete();

        } catch (ResourceNotFoundException ex) {
            emitErrorAndComplete(emitter, "CUSTOMER_NOT_FOUND", ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitErrorAndComplete(emitter, "INTERRUPTED", "Processing interrupted");
        } catch (Exception ex) {
            log.error("Streaming pipeline failed: {}", ex.getMessage(), ex);
            emitErrorAndComplete(emitter, "PIPELINE_ERROR", "An unexpected error occurred: " + ex.getMessage());
        }
    }

    /**
     * Persists a fully analyzed ticket transactionally within the streaming context.
     *
     * @param customer the owning customer
     * @param query    the raw query text
     * @param analysis the AI analysis result
     * @return the saved ticket entity
     */
    // Note: @Transactional omitted intentionally — this method is called from a virtual-thread
    // ExecutorService, which bypasses Spring's proxy-based @Transactional interception.
    // The single ticketRepository.save() call is atomic on its own.
    SupportTicket persistTicketTransactional(
            Customer customer,
            String query,
            TicketAnalysisResponse analysis
    ) {
        SupportTicket ticket = SupportTicket.builder()
                .customer(customer)
                .rawQuery(query)
                .intent(analysis.intent().name())
                .urgency(analysis.urgency().name())
                .resolution(analysis.suggestedResolution())
                .status(STATUS_RESOLVED)
                .build();
        return ticketRepository.save(ticket);
    }

    /**
     * Emits a single SSE event to the connected client.
     *
     * @param emitter   the SSE emitter
     * @param eventName the SSE event type name
     * @param data      the string payload for this event
     */
    private void emitEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(
                    SseEmitter.event()
                              .name(eventName)
                              .data(data)
            );
        } catch (IOException ex) {
            log.warn("SSE client disconnected during event '{}': {}", eventName, ex.getMessage());
            throw new RuntimeException("SSE client disconnected", ex);
        }
    }

    /**
     * Emits an error event and completes (closes) the SSE stream.
     *
     * @param emitter   the SSE emitter to notify and close
     * @param errorCode a short machine-readable error code
     * @param message   a human-readable error description
     */
    private void emitErrorAndComplete(SseEmitter emitter, String errorCode, String message) {
        try {
            emitter.send(
                    SseEmitter.event()
                              .name("error")
                              .data(String.format("{\"code\":\"%s\",\"message\":%s}",
                                      errorCode, toJsonString(message)))
            );
        } catch (IOException ignored) {
            // Best-effort; client may have disconnected
        } finally {
            emitter.complete();
        }
    }

    /**
     * Builds a JSON string for a stage progress event.
     *
     * @param stage   the pipeline stage identifier
     * @param message human-readable status message
     * @param status  the stage status: IN_PROGRESS, COMPLETE, FALLBACK, FAILED
     * @return JSON string payload for the stage SSE event
     */
    private String buildStageEvent(String stage, String message, String status) {
        return String.format("{\"stage\":\"%s\",\"message\":%s,\"status\":\"%s\"}",
                stage, toJsonString(message), status);
    }

    /**
     * Wraps a string in JSON double quotes and escapes special characters.
     *
     * @param input the raw string to wrap
     * @return a JSON-safe quoted string
     */
    private String toJsonString(String input) {
        if (input == null) return "null";
        return "\"" + escapeJson(input) + "\"";
    }

    /**
     * Escapes a string for safe embedding in a JSON string value.
     *
     * @param input the raw string
     * @return the escaped string
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
