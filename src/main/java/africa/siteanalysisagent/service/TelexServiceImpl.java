package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelexServiceImpl implements TelexService {

    private static final String TELEX_WEBHOOK_BASE = "https://ping.telex.im/v1/webhooks";
    private final RestTemplate restTemplate;
    private final Map<String, String> channelTokens = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> channelQueues = new ConcurrentHashMap<>();
    private final Executor telexExecutor = Executors.newFixedThreadPool(4);

    public TelexServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Handles incoming webhook from Telex
     */
    public ResponseEntity<Map<String, Object>> handleIncomingMessage(
            String channelId,
            String webhookToken,
            String message,
            List<Button> buttons) {

        // Register the channel if new
        registerChannel(channelId, webhookToken);
        log.info("Message received from channel {}", channelId);

        // Process message asynchronously
        processMessageAsync(channelId, message, buttons);

        // Return immediate acknowledgment
        return ResponseEntity.ok(Map.of(
                "status", "processing",
                "channel_id", channelId,
                "timestamp", System.currentTimeMillis()
        ));
    }

    private void registerChannel(String channelId, String webhookToken) {
        if (webhookToken == null || webhookToken.isBlank()) {
            throw new IllegalArgumentException("Invalid webhook token");
        }
        channelTokens.put(channelId, webhookToken);
        log.info("Registered channel {} with token {}", channelId, webhookToken);
    }

    private void processMessageAsync(String channelId, String message, List<Button> buttons) {
        CompletableFuture.runAsync(() -> {
            try {
                sendMessage(channelId, message, buttons)
                        .thenAccept(response -> {
                            if (!response.getStatusCode().is2xxSuccessful()) {
                                log.error("Failed to send response to channel {}", channelId);
                            }
                        });
            } catch (Exception e) {
                log.error("Error processing message for channel {}", channelId, e);
            }
        }, telexExecutor);
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message, List<Button> buttons) {
        return enqueueForChannel(channelId, () -> {
            String webhookToken = channelTokens.get(channelId);
            if (webhookToken == null) {
                log.error("Channel {} not registered", channelId);
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest().body("Channel not registered")
                );
            }

            String webhookUrl = TELEX_WEBHOOK_BASE + "/" + webhookToken;
            return CompletableFuture.supplyAsync(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    Map<String, Object> payload = createPayload(channelId, message, buttons);
                    log.debug("Sending to channel {} via {}", channelId, webhookUrl);

                    return restTemplate.postForEntity(
                            webhookUrl,
                            new HttpEntity<>(payload, headers),
                            String.class
                    );
                } catch (Exception e) {
                    log.error("Failed to send to channel {}", channelId, e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to send message");
                }
            }, telexExecutor);
        });
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message) {
        return sendMessage(channelId, message, null);
    }

    private Map<String, Object> createPayload(String channelId, String message, List<Button> buttons) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username","site-analyzer");
        payload.put("status", "success");
        payload.put("channel_id", channelId);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());

        if (buttons != null && !buttons.isEmpty()) {
            payload.put("buttons", formatButtons(buttons));
        }
        return payload;
    }

    private List<Map<String, String>> formatButtons(List<Button> buttons) {
        return buttons.stream()
                .map(btn -> Map.of(
                        "text", btn.getText(),
                        "value", btn.getValue(),
                        "action", btn.getAction()
                ))
                .toList();
    }

    private CompletableFuture<ResponseEntity<String>> enqueueForChannel(
            String channelId,
            Supplier<CompletableFuture<ResponseEntity<String>>> operation) {

        CompletableFuture<ResponseEntity<String>> result = new CompletableFuture<>();

        channelQueues.compute(channelId, (key, existingFuture) -> {
            CompletableFuture<Void> lastFuture = existingFuture != null
                    ? existingFuture
                    : CompletableFuture.completedFuture(null);

            return lastFuture.thenComposeAsync(
                    v -> operation.get()
                            .thenAccept(result::complete)
                            .exceptionally(ex -> {
                                result.completeExceptionally(ex);
                                return null;
                            }),
                    telexExecutor
            );
        });

        return result;
    }
}