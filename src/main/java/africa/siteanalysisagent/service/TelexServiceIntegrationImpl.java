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
                "target_url": "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/telex-webhook",
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
                LocalDateTime.now()
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

        try {
            // 1. Process the message
            ChatMessage chatMessage = new ChatMessage(
                    "telex-user-" + channelId,
                    message,
                    null,
                    LocalDateTime.now()
            );

            ChatResponse response = lynxService.processMessage(chatMessage);

            // 2. Send response back to Telex
            telexService.sendMessage(
                    channelId,
                    response.getMessage(),
                    response.getButtons()
            );

            // 3. Return acknowledgment
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "channel_id", channelId,
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Error processing Telex webhook", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage(),
                            "channel_id", channelId
                    ));
        }
    }


    private Map<String, Object> processConfirmedScan(String channelId) {
        if (!userUrls.containsKey(channelId)) {
            return Map.of("error", "No URL found. Please enter a valid URL first.");
        }

        String urlToScan = userUrls.get(channelId);
        try {
            SiteAnalysis analysis = metaAnalysisService.analyzeSite(channelId,urlToScan);
            userUrls.remove(channelId);

            return Map.of(
                    "url", urlToScan,
                    "status", "success"
            );
        } catch (Exception e) {
            log.error("❌ Error during scanning: {}", e.getMessage(), e);
            return Map.of("error", "Failed to process scan", "status", "failed");
        }
    }

    private boolean isValidUrl(String text) {
        return text != null && !text.isEmpty() && URL_PATTERN.matcher(text).matches();
    }

    private String sanitizeInput(String input) {
        return input == null ? "" : input.replaceAll("<[^>]*>", "").trim();
    }
}
