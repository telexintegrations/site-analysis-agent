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
    private final Map<String, CompletableFuture<Void>> channelSequence = new ConcurrentHashMap<>();
    private final Executor telexExecutor = Executors.newSingleThreadExecutor();

    public TelexServiceImpl() {
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(1500);
        return new RestTemplate(factory);
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message) {
        return enqueueForChannel(channelId, () -> {
            String webhookUrl = getWebhookUrl(channelId);

            if (webhookUrl == null) {
                log.error("❌ No webhook URL found for channel '{}'", channelId);
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body("Webhook URL not found.")
                );
            }

            Map<String, Object> payload = createPayload(channelId, message);

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(500); // Maintain your original delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return sendToTelex(webhookUrl, payload);
            }, telexExecutor);
        });
    }

    private Map<String, Object> createPayload(String channelId, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);
        payload.put("channel_id", channelId);
        return payload;
    }

    private ResponseEntity<String> sendToTelex(String webhookUrl, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            log.info("📤 Sending message to Telex: {}", payload);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://ping.telex.im/v1/webhooks/0195d964-f53e-773b-b8d2-1937b5572911",
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Message sent successfully");
            } else {
                log.error("❌ Telex API Error: {}", response.getBody());
            }
            return response;

        } catch (Exception e) {
            log.error("❌ Failed to send message to Telex", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send message");
        }
    }


    public void updateWebhookUrl(String channelId, List<Setting> settings) {
        String webhookUrl = settings.stream()
                .filter(setting -> "webhook_url".equals(setting.label()))
                .map(Setting::defaultValue)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse("https://ping.telex.im/v1/webhooks/0195d964-f53e-773b-b8d2-1937b5572911"); // Don't fall back to hardcoded URL

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


    private String getWebhookUrl(String channelId) {
        return webhookCache.getOrDefault(channelId, ""); // Return cached webhook if available
    }

    // Maintain your original sequencing logic
    private CompletableFuture<ResponseEntity<String>> enqueueForChannel(
            String channelId,
            Supplier<CompletableFuture<ResponseEntity<String>>> operation) {
        CompletableFuture<ResponseEntity<String>> resultFuture = new CompletableFuture<>();

        CompletableFuture<Void> lastFuture = channelSequence.getOrDefault(
                channelId, CompletableFuture.completedFuture(null)
        );

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

        channelSequence.put(channelId, newFuture);
        return resultFuture;
    }

}