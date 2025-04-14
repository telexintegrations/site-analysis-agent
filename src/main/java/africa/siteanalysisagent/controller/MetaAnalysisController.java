package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.*;
import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.ChatResponse;
import africa.siteanalysisagent.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
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
            @RequestBody Map<String, Object> requestBody) {

        // 1. Extract Telex parameters from THEIR format
        String channelId = (String) requestBody.get("channel_id");
        if (channelId == null || channelId.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Missing or empty channel_id")
            );
        }
        String userMessage;
        try {
            userMessage = extractMessageFromTelexFormat(requestBody);
            if (userMessage == null || userMessage.isBlank()) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body("Message content cannot be empty")
                );
            }
        } catch (Exception e) {
            log.error("Message extraction failed", e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Invalid message format")
            );
        }


        // 3. Process message
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId(channelId);
        chatMessage.setUserMessage(userMessage);

        ChatResponse response = lynxService.processMessage(chatMessage);

        // 4. Send response back to Telex
        telexService.sendMessage(channelId, response.getMessage());

        return CompletableFuture.completedFuture(ResponseEntity.ok(response));
    }

    private String extractMessageFromTelexFormat(Map<String, Object> requestBody) {
        // 1. Get raw message
        Object rawMessage = requestBody.get("message");
        if (rawMessage == null) {
            throw new IllegalArgumentException("Missing message field");
        }

        // 2. Handle different message formats
        if (rawMessage instanceof String) {
            String messageStr = (String) rawMessage;
            if (messageStr.isBlank()) {
                throw new IllegalArgumentException("Empty message content");
            }

            // 3. Parse HTML if needed
            if (messageStr.startsWith("<") && messageStr.endsWith(">")) {
                return Jsoup.parse(messageStr).text();
            }
            return messageStr;
        }

        throw new IllegalArgumentException("Unsupported message type: " + rawMessage.getClass());
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