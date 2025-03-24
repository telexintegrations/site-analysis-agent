package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Setting;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelexServiceIntegrationImpl implements TelexServiceIntegration {

    private final MetaAnalysisService metaAnalysisService;

    private final BotService botService;
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
                "integration_type": "modifier",
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
                        "label": "webhook_url",
                        "type": "text",
                        "description": "provide your telex channel webhook url",
                        "default": "",
                        "required": true
                    }
                ],
                "target_url": "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/scrape",
                "tick_url": "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/scrape"
                }
            }
            """;
              


    @Override
    public TelexIntegration getTelexConfig() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);

        // Parse Telex settings JSON
        TelexIntegration telexIntegration = objectMapper.readValue(TELEX_CONFIG_JSON, TelexIntegration.class);

        return telexIntegration;
    }

    @Override
    public Map<String, Object> scrapeAndGenerateUrlReport(TelexUserRequest telexUserRequest) throws IOException {
        String userInput = sanitizeInput(telexUserRequest.text());
        String channelId = telexUserRequest.channelId(); // Extract channelId directly

        log.info("📩 Processing URL '{}' from Channel '{}'", userInput, channelId);


        // Update webhook URL dynamically
        telexService.updateWebhookUrl(channelId, telexUserRequest.settings());
        // Delegate all user commands to the BotService
        botService.handleEvent(telexUserRequest);

        // Return a response indicating the command was forwarded
        return Map.of("message", "Command forwarded to bot service");
    }

    private Map<String, Object> processConfirmedScan(String channelId) {
        if (!userUrls.containsKey(channelId)) {
            return Map.of("error", "No URL found. Please enter a valid URL first.");
        }

        String urlToScan = userUrls.get(channelId);
        String scanId = UUID.randomUUID().toString();
        log.info("📡 Scanning confirmed for '{}'", urlToScan);

        try {
            metaAnalysisService.generateSeoReport(urlToScan, scanId, channelId);
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
