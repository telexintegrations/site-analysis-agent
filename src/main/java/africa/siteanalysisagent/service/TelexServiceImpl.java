package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.Setting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableAsync // Enable asynchronous execution
public class TelexServiceImpl implements TelexService {

    private final RestTemplate restTemplate;
    private final Map<String, String> webhookCache = new HashMap<>(); // Cache webhook URLs per channel

    public TelexServiceImpl() {
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(150000);
        factory.setReadTimeout(150000);
        return new RestTemplate(factory);
    }

    @Override
    @Async // Mark this method as asynchronous
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message) {
        String webhookUrl = getWebhookUrl(channelId);

        if (webhookUrl == null) {
            log.error("❌ No webhook URL found for channel '{}'.", channelId);
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Webhook URL not found."));
        }

        if (message == null || message.isEmpty()) {
            log.error("❌ Message is empty.");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Message is empty."));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);
        payload.put("channel_id", channelId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000); // 1-second delay between messages to avoid rate limits
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return sendToTelex(webhookUrl,payload);
        });
    }

    @Override
    @Async // Mark this method as asynchronous
    public CompletableFuture<ResponseEntity<String>> sendInteractiveMessage(String channelId, String message, List<Button> buttons) {
        String webhookUrl = getWebhookUrl(channelId);

        if (webhookUrl == null) {
            log.error("❌ No webhook URL found for channel '{}'.", channelId);
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Webhook URL not found."));
        }


        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);
        payload.put("channel_id", channelId);

        // Properly format buttons
        List<Map<String, String>> buttonList = new ArrayList<>();
        for (Button button : buttons) {
            Map<String, String> btn = new HashMap<>();
            btn.put("text", button.getText());
            btn.put("value", button.getValue());
            buttonList.add(btn);
        }
        payload.put("buttons", buttonList);

        return CompletableFuture.supplyAsync(() -> sendToTelex(webhookUrl,payload));
    }

    @Override
    @Async // Mark this method as asynchronous
    public CompletableFuture<ResponseEntity<String>> notifyTelex(String message, String channelId) {
        try {
            String webhookUrl = getWebhookUrl(channelId); // Fetch dynamic webhook URL

            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.error("❌ No webhook URL found for channel '{}'.", channelId);
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Webhook URL not found."));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_name", "web_scrape");
            payload.put("username", "site-analyzer");
            payload.put("status", "success");
            payload.put("message", message);
            payload.put("channel_id", channelId);

            return CompletableFuture.supplyAsync(() -> sendToTelex(webhookUrl,payload));
        } catch (Exception e) {
            log.error("Failed to send Telex notification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send Telex notification."));
        }
    }

    public void updateWebhookUrl(String channelId, List<Setting> settings) {
        String webhookUrl = settings.stream()
                .filter(setting -> "webhook_url".equals(setting.label()))
                .map(Setting::defaultValue)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null); // Don't fall back to hardcoded URL

        if (webhookUrl != null) {
            // Validate URL format
            if (!webhookUrl.startsWith("http")) {
                log.error("❌ Invalid webhook URL format for channel {}: {}", channelId, webhookUrl);
                return;
            }
            webhookCache.put(channelId, webhookUrl);
            log.info("✅ Updated webhook URL for channel '{}': {}", channelId, webhookUrl);
        } else {
            log.error("❌ No valid webhook URL found in settings for channel {}", channelId);
        }
    }

    private ResponseEntity<String> sendToTelex(String webhookUrl,Map<String, Object> payload) {

        // Validate URL first
        if (webhookUrl == null || !webhookUrl.startsWith("http")) {
            log.error("❌ Invalid webhook URL: {}", webhookUrl);
            return ResponseEntity.badRequest().body("Invalid webhook URL configuration");
        }
        int maxRetries = 3; // Retry up to 3 times
        int retryDelay = 1000; // Start with 1-second delay

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("accept", "application/json");

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                log.info("📤 Sending message to Telex (attempt {}): {}", attempt, payload);

                ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Message sent successfully: {}", response.getBody());
                    return response; // Success, return response
                } else if (response.getStatusCode().value() == 429) {
                    log.warn("⚠️ 429 Too Many Requests - Retrying in {}ms...", retryDelay);
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // Exponential backoff
                } else {
                    log.error("❌ Telex API Error: {}", response.getBody());
                    return response;
                }

            } catch (Exception e) {
                log.error("❌ Failed to send message to Telex: {}", e.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Failed after multiple retries");
    }


    private String getWebhookUrl(String channelId) {
        return webhookCache.getOrDefault(channelId, ""); // Return cached webhook if available
    }


}