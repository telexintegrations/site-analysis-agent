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
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/meta-analysis")
@RequiredArgsConstructor
@Slf4j
public class MetaAnalysisController {

    private final LynxService lynxService;
    private final TelexService telexService;
    private final TelexServiceIntegration telexServiceIntegration;

    @PostMapping("/interact")
    public CompletableFuture<ResponseEntity<?>> handleUserMessage(
            @Valid @RequestBody ChatMessage chatMessage,
            @RequestHeader(value = "X-Telex-Channel-Id", required = false) String channelId,
            @RequestHeader(value = "X-Telex-Webhook-Token", required = false) String webhookToken) {

        // Set default values for the chat message
        if (channelId == null || webhookToken == null) {
            log.warn("Missing Telex headers - cannot process message");
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Telex channel ID and token required")
            );
        }

        chatMessage.setUserId(channelId);
        chatMessage.setTimestamp(LocalDateTime.now());


        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatResponse response = lynxService.processMessage(chatMessage);


                    telexService.sendMessage(channelId, response.getMessage());
                    return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Error processing message", e);
            telexService.sendMessage(channelId, "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
            }
        });
    }

    @PostMapping("/telex-webhook")
    public ResponseEntity<Map<String, Object>> handleTelexWebhook(
            @RequestBody TelexUserRequest request) {

        // Extract channel info from the incoming webhook request
        String channelId = request.channelId();
        String webhookToken = request.webhookToken();
        String message = request.text();

        // Validate required fields
        if (channelId == null || channelId.isBlank()) {
            log.error("Missing channelId in Telex webhook request");
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "channelId is required in webhook request",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }

        // Process the webhook
        try {
            log.info("Received webhook from channel {}", channelId);
            return telexServiceIntegration.handleTelexWebhook(channelId, webhookToken, message);
        } catch (Exception e) {
            log.error("Error processing webhook from channel {}: {}", channelId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to process webhook",
                    "channelId", channelId,
                    "timestamp", LocalDateTime.now().toString()
            ));
        }
    }


    @GetMapping("/telex-config")
    public ResponseEntity<?> getTelexConfiguration() {
        try {
            return ResponseEntity.ok(telexServiceIntegration.getTelexConfig());
        } catch (Exception e) {
            log.error("Failed to get Telex config", e);
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