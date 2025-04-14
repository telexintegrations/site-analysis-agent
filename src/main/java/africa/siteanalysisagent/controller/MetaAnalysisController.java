package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.*;
import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.ChatResponse;
import africa.siteanalysisagent.service.*;
import jakarta.servlet.http.HttpServletRequest;
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
            @RequestBody Map<String, Object> requestBody) { // Parse raw JSON
        log.info("Raw request body: {}", requestBody);


        // Extract channelId/webhookToken from JSON body (not headers)
        String channelId = (String) requestBody.get("channelId");
        String webhookToken = (String) requestBody.get("webhookToken");
        String userMessage = (String) requestBody.get("message");

        // Validate
        if (channelId == null || webhookToken == null) {
            log.error("Missing Telex params in body. Received: {}", requestBody.keySet());
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Missing channelId/webhookToken in JSON body")
            );
        }

        // Process message
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId(channelId); // Use Telex's channelId directly
        chatMessage.setUserMessage(userMessage);

        ChatResponse response = lynxService.processMessage(chatMessage);
        telexService.sendMessage(channelId, response.getMessage());

        return CompletableFuture.completedFuture(ResponseEntity.ok(response));
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