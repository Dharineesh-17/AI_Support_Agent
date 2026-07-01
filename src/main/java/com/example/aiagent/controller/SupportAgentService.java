package com.example.aiagent.controller;

import com.example.aiagent.dto.TicketRequest;
import com.example.aiagent.dto.TicketResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.aiagent.domain.Intent;
import com.example.aiagent.domain.Urgency;



@Service
public class SupportAgentService {

    private static final Logger log = LoggerFactory.getLogger(SupportAgentService.class);

    private final ChatModel chatModel;
    private final ExecutorService executorService;

    public SupportAgentService(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Transactional
    public TicketResponse processAndAnalyzeTicket(TicketRequest request) {
        log.info("Processing ticket query: {}", request.query());
        
        String prompt = "Analyze this customer support issue: " + request.query();
        String aiResponse = chatModel.call(prompt);

        // Fixed to pass all 7 expected parameters to match your custom DTO definition
        return new TicketResponse(
            1L,                                           // id
            request.query(),                              // rawQuery
            "PROCESSED",                                  // status
            null,                                         // intent (Enum or custom Class)
            null,                                         // urgency (Enum or custom Class)
            aiResponse,                                   // resolution text
            LocalDateTime.now()                           // createdAt timestamp
        );
    }

    public SseEmitter streamResolution(String query) {
        SseEmitter emitter = new SseEmitter(60000L);
        
        executorService.submit(() -> {
            try {
                log.info("Streaming resolution tokens for query: {}", query);
                emitter.send(SseEmitter.event().name("thinking").data("Processing query..."));
                
                String result = chatModel.call(query);
                
                emitter.send(SseEmitter.event().name("delta").data(result));
                emitter.complete();
            } catch (IOException e) {
                log.error("Failed to transmit data tokens via active SSE emitter channel", e);
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("Unexpected runtime exception thrown during AI streaming execution loop", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
