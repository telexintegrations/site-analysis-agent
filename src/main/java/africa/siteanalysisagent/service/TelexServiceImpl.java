package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
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
    private static final String TELEX_WEBHOOK_URL = "https://ping.telex.im/v1/webhooks/01958e7d-78cd-73d4-a9e3-ee05c7a0aab0"; // Update with your actual webhook ID

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
        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Channel ID is not provided.");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Channel ID is not provided."));
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
            return sendToTelex(payload);
        });
    }

    @Override
    @Async // Mark this method as asynchronous
    public CompletableFuture<ResponseEntity<String>> sendInteractiveMessage(String channelId, String message, List<Button> buttons) {
        if (channelId == null || channelId.isEmpty()) {
            log.error("Cannot send message: channel_id is missing.");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Channel ID is missing."));
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

        return CompletableFuture.supplyAsync(() -> sendToTelex(payload));
    }

    @Override
    @Async // Mark this method as asynchronous
    public CompletableFuture<ResponseEntity<String>> notifyTelex(String message, String channelId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_name", "web_scrape");
            payload.put("username", "site-analyzer");
            payload.put("status", "success");
            payload.put("message", message);
            payload.put("channel_id", channelId);

            return CompletableFuture.supplyAsync(() -> sendToTelex(payload));
        } catch (Exception e) {
            log.error("Failed to send Telex notification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send Telex notification."));
        }
    }

    private ResponseEntity<String> sendToTelex(Map<String, Object> payload) {
        int maxRetries = 3; // Retry up to 3 times
        int retryDelay = 1000; // Start with 1-second delay

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("accept", "application/json");

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                log.info("📤 Sending message to Telex (attempt {}): {}", attempt, payload);

                ResponseEntity<String> response = restTemplate.postForEntity(TELEX_WEBHOOK_URL, entity, String.class);

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
}