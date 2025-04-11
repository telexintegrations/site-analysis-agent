package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.*;
import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.ChatResponse;
import africa.siteanalysisagent.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/meta-analysis")
@RequiredArgsConstructor
@Slf4j
public class MetaAnalysisController {

    private final LynxService lynxService;
    private final TelexService telexService;
    private final TelexServiceIntegration integration;

    @PostMapping("/interact")
    public ResponseEntity<?> handleUserMessage(
            @Valid @RequestBody ChatMessage chatMessage,
            @RequestHeader(value = "X-Telex-Channel-Id", required = false) String channelId,
            @RequestHeader(value = "X-Telex-Webhook-Token", required = false) String webhookToken) {

        // Validate input
        if (chatMessage.getUserMessage() == null || chatMessage.getUserMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Message cannot be blank"));
        }

        // Set default values if not provided
        if (chatMessage.getUserId() == null) {
            chatMessage.setUserId("guest-" + UUID.randomUUID());
        }
        if (chatMessage.getTimestamp() == null) {
            chatMessage.setTimestamp(LocalDateTime.now());
        }

        try {
            // Process message through Lynx service
            ChatResponse response = lynxService.processMessage(chatMessage);

            // If coming from Telex channel, send response back
            if (channelId != null && webhookToken != null) {
                telexService.handleIncomingMessage(
                        channelId,
                        webhookToken,
                        response.getMessage(),
                        response.getButtons()
                );
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing message", e);
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.error("Processing failed: " + e.getMessage()));
        }
    }

    @GetMapping("/telex")
    public ResponseEntity<?> getTelexConfiguration() {
        try {
            return ResponseEntity.ok(integration.getTelexConfig());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiErrorResponse(
                            "Configuration error",
                            e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            LocalDate.now().toString()
                    ));
        }
    }
}