package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.SiteAnalysis;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.ChatResponse;
import africa.siteanalysisagent.model.SEOReport;
import africa.siteanalysisagent.model.TelexIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelexServiceIntegrationImpl implements TelexServiceIntegration {

    private final MetaAnalysisService metaAnalysisService;

    private final LynxService lynxService;
    private final TelexService telexService;

    private final Map<String, String> userUrls = new HashMap<>();


    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://)[a-zA-Z0-9]+(.[a-zA-Z0-9]+)+(:[0-9]+)?(/[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=]*)?$");


    private static final String TELEX_CONFIG_JSON = """
            {
              "data": {
                "date": {
                  "created_at": "2025-03-12",
                  "updated_at": "2025-03-12"
                },
                "descriptions": {
                  "app_name": "Site Analysis Agent",
                  "app_description": "Site Analysis agent for Telex Integration: A tool that helps you analyze your website's SEO and meta tags.",
                  "app_logo": "https://lh3.googleusercontent.com/pw/AP1GczPfSJ0ewO2h17zvsr1EG3Kv_2I_Tl3Cgwb16VuYJ-eRo9sX9J7xXN4X0UpiEQsjTY_EpWH_-gjYaYdWO_JROaxEc-uxzuqCY9ZfM9yl2BzwwIoAicYNJROiI4KENYLy3V76X79ya6fEvrrxbmdAKmtS=w830-h828-s-no-gm?authuser=0",
                  "app_url": "https://site-analysis-agent.onrender.com/",
                  "background_color": "#fff"
                },
                "integration_category": "CRM & Customer Support",
                "integration_type": "interval",
                "is_active": true,
                "key_features": [
                  "Single page meta analysis",
                  "Internal link crawling",
                  " Broken link detection",
                  " AI-powered meta suggestions"
                ],
                "author": "Telin",
                "permissions": {
                              "site_analysis_agent": {
                                "always_online": true,
                                "display_name": "Site Analysis Agent"
                       }
                            },
                "settings": [
                    {
                        "label": "interval",
                        "type": "text",
                        "description": "provide your interval",
                        "default": "",
                        "required": true
                    }
                ],
                "target_url": "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/interact",
                "tick_url": "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/interact"
                }
            }
            """;



    @Override
    public TelexIntegration getTelexConfig() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        return objectMapper.readValue(TELEX_CONFIG_JSON, TelexIntegration.class);

    }

    @Override
    public ResponseEntity<Map<String, Object>> scrapeAndGenerateUrlReport(TelexUserRequest telexUserRequest) throws IOException {
        log.info("📩 Received Telex request from channel {}", telexUserRequest.channelId());

        // 1. Process the incoming message
        ChatMessage chatMessage = new ChatMessage(
                "telex-user-" + telexUserRequest.channelId(),
                telexUserRequest.text(),
                null,
                LocalDateTime.now(),
                telexUserRequest.channelId()
        );

        // 2. Get response from Lynx service
        ChatResponse response = lynxService.processMessage(chatMessage);

        // 3. Send response back to Telex channel
        telexService.sendMessage(
                telexUserRequest.channelId(),
                response.getMessage(),
                response.getButtons()
        ).thenAccept(res -> {
            if (!res.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to send response to channel {}", telexUserRequest.channelId());
            }
        });

        // 4. Return immediate acknowledgment
        return ResponseEntity.ok(Map.of(
                "status", "processing",
                "channel_id", telexUserRequest.channelId(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @Override
    public ResponseEntity<Map<String, Object>> handleTelexWebhook(
            String channelId,
            String webhookToken,
            String message) {

        // 1. Validate input
        if (channelId == null || channelId.isBlank()) {
            return errorResponse("Channel ID is required", null);
        }
        if (message == null || message.isBlank()) {
            return errorResponse("Message cannot be empty", channelId);
        }

        // 2. Register the channel (if not already registered)
        telexService.registerChannel(channelId, webhookToken);

        // 3. Process the message
        ChatMessage chatMessage = new ChatMessage(
                "telex-user-" + channelId,
                message,
                null,
                LocalDateTime.now(),
                channelId
        );

        try {
            ChatResponse response = lynxService.processMessage(chatMessage);

            // 4. Send response back to Telex
            telexService.sendMessage(channelId, response.getMessage(), response.getButtons())
                    .exceptionally(ex -> {
                        log.error("Failed to send response to channel {}", channelId, ex);
                        return null;
                    });

            return successResponse(channelId);

        } catch (Exception e) {
            log.error("Error processing message for channel {}", channelId, e);
            // Send error message back to Telex
            telexService.sendMessage(channelId, "Error processing your request: " + e.getMessage(), null);
            return errorResponse("Processing failed", channelId);
        }
    }

    private ResponseEntity<Map<String, Object>> successResponse(String channelId) {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "channel_id", channelId,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String error, String channelId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", error);
        if (channelId != null) {
            response.put("channel_id", channelId);
        }
        return ResponseEntity.badRequest().body(response);
    }


    private boolean isValidUrl(String text) {
        return text != null && !text.isEmpty() && URL_PATTERN.matcher(text).matches();
    }

    private String sanitizeInput(String input) {
        return input == null ? "" : input.replaceAll("<[^>]*>", "").trim();
    }
}
