package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.Setting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelexServiceImpl implements TelexService {

    private final RestTemplate restTemplate;
    private final Map<String, String> webhookCache = new ConcurrentHashMap<>();

    // Message queue per channel with strict ordering
    private final Map<String, CompletableFuture<Void>> channelQueues = new ConcurrentHashMap<>();
    private final Executor telexExecutor = Executors.newSingleThreadExecutor();

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
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message) {
        return enqueueTelexOperation(channelId, () -> {
            if (message == null || message.isEmpty()) {
                log.error("❌ Message is empty.");
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body("Message is empty.")
                );
            }

            return prepareAndSend(channelId, createPayload(message, null));
        });
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendInteractiveMessage(
            String channelId, String message, List<Button> buttons
    ) {
        return enqueueTelexOperation(channelId, () -> {
            Map<String, Object> payload = createPayload(message, buttons);
            return prepareAndSend(channelId, payload);
        });
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> notifyTelex(String message, String channelId) {
        return enqueueTelexOperation(channelId, () -> prepareAndSend(channelId, createPayload(message, null)));
    }

    // ========== Core Sequencing Logic ========== //

    private CompletableFuture<ResponseEntity<String>> enqueueTelexOperation(
            String channelId,
            Supplier<CompletableFuture<ResponseEntity<String>>> operation
    ) {
        CompletableFuture<ResponseEntity<String>> resultFuture = new CompletableFuture<>();

        // Get or create queue for this channel
        CompletableFuture<Void> lastOperation = channelQueues.getOrDefault(
                channelId, CompletableFuture.completedFuture(null)
        );

        // Chain the new operation
        CompletableFuture<Void> newOperation = lastOperation;

        // Update the channel queue
        channelQueues.put(channelId, newOperation);
        return resultFuture;
    }

    // ========== Helper Methods ========== //

    private CompletableFuture<ResponseEntity<String>> prepareAndSend(
            String channelId,
            Map<String, Object> payload
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String webhookUrl = getWebhookUrl(channelId);
            if (webhookUrl == null) {
                log.error("❌ No webhook URL for channel {}", channelId);
                return ResponseEntity.badRequest().body("Webhook URL not found");
            }
            return sendToTelex(webhookUrl, payload);
        }, telexExecutor);
    }

    private Map<String, Object> createPayload(String message, List<Button> buttons) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);

        if (buttons != null && !buttons.isEmpty()) {
            List<Map<String, String>> buttonList = new ArrayList<>();
            buttons.forEach(btn -> buttonList.add(Map.of(
                    "text", btn.getText(),
                    "value", btn.getValue()
            )));
            payload.put("buttons", buttonList);
        }
        return payload;
    }

    // ========== Existing Methods (Unchanged) ========== //

    @Override
    public void updateWebhookUrl(String channelId, List<Setting> settings) {
        String webhookUrl = settings.stream()
                .filter(setting -> "webhook_url".equals(setting.label()))
                .map(Setting::defaultValue)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);

        if (webhookUrl != null && webhookUrl.startsWith("http")) {
            webhookCache.put(channelId, webhookUrl);
            log.info("✅ Updated webhook URL for channel '{}'", channelId);
        } else {
            log.error("❌ Invalid webhook URL for channel {}", channelId);
        }
    }

    private ResponseEntity<String> sendToTelex(String webhookUrl, Map<String, Object> payload) {

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
        return webhookCache.get(channelId);
    }
}
