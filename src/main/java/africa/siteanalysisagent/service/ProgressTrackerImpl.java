package africa.siteanalysisagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressTrackerImpl implements ProgressTracker {

    private final TelexService telexService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // Track message sequences per channel
    private final Map<String, CompletableFuture<Void>> channelQueues = new ConcurrentHashMap<>();
    private final AtomicLong globalSequence = new AtomicLong(0);
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);

    private static final String BOT_IDENTIFIER = "#bot_message";
    private static final String WS_DESTINATION_PREFIX = "/queue/progress/";  // Changed to queue for direct messaging
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Override
    public CompletableFuture<Void> sendProgress(String scanId, String channelId, int progress, String message) {
        return enqueueMessage(channelId, () -> {
            Map<String, Object> payload = createPayload(scanId, progress, message);
            return sendDualMessage(channelId, payload, "progress");
        });
    }

    @Override
    public CompletableFuture<Void> sendReport(String scanId, String channelId, String title, String reportContent) {
        return enqueueMessage(channelId, () -> {
            Map<String, Object> payload = createPayload(scanId, 100, title);
            payload.put("content", reportContent);
            payload.put("type", "report");
            return sendDualMessage(channelId, payload, "report");
        });
    }

    @Override
    public CompletableFuture<Void> sendAlert(String channelId, String alertMessage) {
        return enqueueMessage(channelId, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", ensureBotIdentifier(alertMessage));
            payload.put("type", "alert");
            payload.put("timestamp", System.currentTimeMillis());
            return sendDualMessage(channelId, payload, "alert");
        });
    }

    private CompletableFuture<Void> sendDualMessage(String channelId, Map<String, Object> payload, String messageType) {
        return CompletableFuture.runAsync(() -> {
            try {
                String jsonPayload = objectMapper.writeValueAsString(payload);

                // 1. First try WebSocket
                boolean wsSuccess = sendWithRetry(() -> {
                    messagingTemplate.convertAndSendToUser(
                            channelId,
                            WS_DESTINATION_PREFIX + channelId,
                            jsonPayload
                    );
                    return true;
                }, "WebSocket", channelId);

                // 2. Fallback to Telex if WebSocket fails
                if (!wsSuccess) {
                    String telexMessage = formatForTelex(payload, messageType);
                    sendWithRetry(() -> {
                        telexService.sendMessage(channelId, telexMessage).join();
                        return true;
                    }, "Telex", channelId);
                }

            } catch (JsonProcessingException e) {
                log.error("JSON serialization failed for channel {}: {}", channelId, e.getMessage());
            }
        });
    }

    @Retryable(maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    private boolean sendWithRetry(Supplier<Boolean> sendOperation, String serviceType, String channelId) {
        try {
            return sendOperation.get();
        } catch (Exception e) {
            log.warn("{} send failed for channel {} (retrying): {}", serviceType, channelId, e.getMessage());
            throw e; // Trigger retry
        }
    }

    private Map<String, Object> createPayload(String scanId, int progress, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scanId", scanId);
        payload.put("progress", progress);
        payload.put("message", ensureBotIdentifier(message));
        payload.put("sequence", globalSequence.getAndIncrement());
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private String formatForTelex(Map<String, Object> payload, String type) {
        switch (type) {
            case "progress":
                return String.format(
                        "🔍 **Progress Update**\n━━━━━━━━━━━━━━━━━━\n" +
                                "📊 Progress: %d%%\n📢 Status: %s\n%s",
                        payload.get("progress"), payload.get("message"), BOT_IDENTIFIER
                );
            case "report":
                return String.format("**%s**\n\n%s\n%s",
                        payload.get("message"), payload.get("content"), BOT_IDENTIFIER);
            default:
                return payload.get("message").toString();
        }
    }

    private CompletableFuture<Void> enqueueMessage(String channelId, Supplier<CompletableFuture<Void>> messageTask) {
        CompletableFuture<Void> lastFuture = channelQueues.getOrDefault(
                channelId, CompletableFuture.completedFuture(null)
        );

        CompletableFuture<Void> newFuture = lastFuture.thenComposeAsync(ignored ->
                        messageTask.get().exceptionally(ex -> {
                            log.error("Message failed for channel {}: {}", channelId, ex.getMessage());
                            return null;
                        }),
                retryExecutor
        );

        channelQueues.put(channelId, newFuture);
        return newFuture;
    }

    private String ensureBotIdentifier(String message) {
        return message.trim() + (message.endsWith(BOT_IDENTIFIER) ? "" : " " + BOT_IDENTIFIER);
    }
}