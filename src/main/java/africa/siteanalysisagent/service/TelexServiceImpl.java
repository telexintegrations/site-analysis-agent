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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableAsync // Enable asynchronous execution
public class TelexServiceImpl implements TelexService {

    private final RestTemplate restTemplate;
    private final Map<String, String> webhookCache = new HashMap<>(); // Cache webhook URLs per channel

    private final Map<String, CompletableFuture<Void>> channnelSequence = new ConcurrentHashMap<>();
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
        return enqueueForChannel(channelId, () -> {
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
                    Thread.sleep(500); // 1-second delay between messages to avoid rate limits
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return sendToTelex("https://ping.telex.im/v1/webhooks/019582d6-476b-7d12-8721-37f9ebf858b4", payload);
            }, telexExecutor);
        });
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendInteractiveMessage(String channelId, String message, List<Button> buttons) {
        return enqueueForChannel(channelId, () -> {
            // Your original validation
            String webhookUrl = getWebhookUrl(channelId);
            if (webhookUrl == null) {
                log.error("❌ No webhook URL found for channel '{}'.", channelId);
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body("Webhook URL not found.")
                );
            }

            // Your original payload construction
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_name", "web_scraper");
            payload.put("username", "site-analyzer");
            payload.put("status", "success");
            payload.put("message", message);
            payload.put("channel_id", channelId);

            // Your original button formatting
            List<Map<String, String>> buttonList = new ArrayList<>();
            for (Button button : buttons) {
                Map<String, String> btn = new HashMap<>();
                btn.put("text", button.getText());
                btn.put("value", button.getValue());
                buttonList.add(btn);
            }
            payload.put("buttons", buttonList);

            // Your original sending logic
            return CompletableFuture.supplyAsync(() -> sendToTelex("https://ping.telex.im/v1/webhooks/019582d6-476b-7d12-8721-37f9ebf858b4", payload), telexExecutor);
        });
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> notifyTelex(String message, String channelId) {
        return enqueueForChannel(channelId, () -> {
            try {
                // Your original validation
                String webhookUrl = getWebhookUrl(channelId);
                if (webhookUrl == null || webhookUrl.isBlank()) {
                    log.error("❌ No webhook URL found for channel '{}'.", channelId);
                    return CompletableFuture.completedFuture(
                            ResponseEntity.badRequest().body("Webhook URL not found.")
                    );
                }

                // Your original payload construction
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("event_name", "web_scraper");
                payload.put("username", "site-analyzer");
                payload.put("status", "success");
                payload.put("message", message);
                payload.put("channel_id", channelId);

                // Your original sending logic
                return CompletableFuture.supplyAsync(() -> sendToTelex("https://ping.telex.im/v1/webhooks/019582d6-476b-7d12-8721-37f9ebf858b4", payload), telexExecutor);
            } catch (Exception e) {
                log.error("Failed to send Telex notification: {}", e.getMessage(), e);
                return CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Failed to send Telex notification.")
                );
            }
        });
    }

    // Add this helper method for sequencing
    private CompletableFuture<ResponseEntity<String>> enqueueForChannel(
            String channelId,
            Supplier<CompletableFuture<ResponseEntity<String>>> operation
    ) {
        CompletableFuture<ResponseEntity<String>> resultFuture = new CompletableFuture<>();

        // Get or create the sequence for this channel
        CompletableFuture<Void> lastFuture = channnelSequence.getOrDefault(
                channelId, CompletableFuture.completedFuture(null)
        );

        // Chain the new operation
        CompletableFuture<Void> newFuture = lastFuture.thenComposeAsync(ignored -> {
            try {
                return operation.get()
                        .thenAccept(response -> resultFuture.complete(response))
                        .exceptionally(ex -> {
                            resultFuture.completeExceptionally(ex);
                            return null;
                        });
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
                return CompletableFuture.completedFuture(null);
            }
        }, telexExecutor);

        // Update the channel sequence
        channnelSequence.put(channelId, newFuture);
        return resultFuture;
    }

    public void updateWebhookUrl(String channelId, List<Setting> settings) {
        String webhookUrl = settings.stream()
                .filter(setting -> "webhook_url".equals(setting.label()))
                .map(Setting::defaultValue)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse("https://ping.telex.im/v1/webhooks/019582d6-476b-7d12-8721-37f9ebf858b4"); // Don't fall back to hardcoded URL

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

                ResponseEntity<String> response = restTemplate.postForEntity("https://ping.telex.im/v1/webhooks/019582d6-476b-7d12-8721-37f9ebf858b4", entity, String.class);

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