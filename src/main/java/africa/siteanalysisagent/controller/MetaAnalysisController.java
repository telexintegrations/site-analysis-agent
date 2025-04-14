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
        if (chatMessage.getUserId() == null) {
            chatMessage.setUserId(channelId != null ?
                    "telex-user-" + channelId :
                    "guest-" + UUID.randomUUID());
        }
        if (chatMessage.getTimestamp() == null) {
            chatMessage.setTimestamp(LocalDateTime.now());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatResponse response = lynxService.processMessage(chatMessage);

                // Only try to send back to Telex if we have both channel ID and token
                if (channelId != null && webhookToken != null) {
                    telexService.registerChannel(channelId, webhookToken);
                    telexService.sendMessage(channelId, response.getMessage(), response.getButtons())
                            .exceptionally(ex -> {
                                log.error("Failed to send to Telex channel {}: {}", channelId, ex.getMessage());
                                return null;
                            });
                }

                return ResponseEntity.ok(response);

            } catch (Exception e) {
                log.error("Error processing message", e);

                // If we have a channel ID, try to send the error back to Telex
                if (channelId != null && webhookToken != null) {
                    telexService.sendMessage(channelId, "Error: " + e.getMessage(), null);
                }

                return ResponseEntity.internalServerError()
                        .body(ChatResponse.error("Processing failed: " + e.getMessage()));
            }
        });
    }

    @PostMapping("/telex-webhook")
    public ResponseEntity<Map<String, Object>> handleTelexWebhook(
            @RequestHeader(value = "X-Telex-Channel-Id", required = false) String channelId,
            @RequestHeader(value = "X-Telex-Webhook-Token", required = false) String webhookToken,
            @RequestBody String message) {

        if (channelId == null || webhookToken == null) {
            log.warn("Missing Telex headers in webhook request");
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", "X-Telex-Channel-Id and X-Telex-Webhook-Token headers are required",
                            "timestamp", LocalDateTime.now()
                    ));
        }

        return telexServiceIntegration.handleTelexWebhook(channelId, webhookToken, message);
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