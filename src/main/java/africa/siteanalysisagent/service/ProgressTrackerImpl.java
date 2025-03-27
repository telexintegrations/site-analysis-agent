package africa.siteanalysisagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    private static final String BOT_IDENTIFIER = "#bot_message";
    private static final String WS_DESTINATION_PREFIX = "/queue/topic/";

    @Override
    public CompletableFuture<Void> sendProgress(String scanId, String channelId, int progress, String message) {
        return enqueueMessage(channelId, () -> {
            try {
                // Prepare payload
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("scanId", scanId);
                payload.put("progress", progress);
                payload.put("message", ensureBotIdentifier(message));
                payload.put("sequence", globalSequence.getAndIncrement());
                payload.put("timestamp", System.currentTimeMillis());

                String jsonPayload = objectMapper.writeValueAsString(payload);

                // Send via WebSocket using SimpMessagingTemplate
                messagingTemplate.convertAndSend(WS_DESTINATION_PREFIX + channelId, jsonPayload);

                // Send to Telex
                String telexMessage = formatTelexMessage(progress, message);
                return telexService.sendMessage(channelId, telexMessage)
                        .thenAccept(response -> logDeliveryStatus("progress", channelId, response));
            } catch (JsonProcessingException e) {
                log.error("JSON serialization failed", e);
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendReport(String scanId, String channelId, String title, String reportContent) {
        return enqueueMessage(channelId, () -> {
            String formattedContent = formatReport(title, reportContent);

            // WebSocket
            messagingTemplate.convertAndSend(WS_DESTINATION_PREFIX + channelId, formattedContent);

            // Telex
            return telexService.sendMessage(channelId, formattedContent)
                    .thenAccept(response -> logDeliveryStatus("report", channelId, response));
        });
    }

    @Override
    public CompletableFuture<Void> sendAlert(String channelId, String alertMessage) {
        return enqueueMessage(channelId, () -> {
            String formattedMessage = ensureBotIdentifier(alertMessage);

            // WebSocket
            messagingTemplate.convertAndSend(WS_DESTINATION_PREFIX + channelId, formattedMessage);

            // Telex
            return telexService.sendMessage(channelId, formattedMessage)
                    .thenAccept(response -> logDeliveryStatus("alert", channelId, response));
        });
    }

    // ========== Private Helpers ========== //

    private CompletableFuture<Void> enqueueMessage(String channelId, Supplier<CompletableFuture<Void>> messageTask) {
        CompletableFuture<Void> lastFuture = channelQueues.getOrDefault(
                channelId, CompletableFuture.completedFuture(null)
        );

        CompletableFuture<Void> newFuture = lastFuture.thenComposeAsync(ignored ->
                messageTask.get().exceptionally(ex -> {
                    log.error("Message failed for channel {}: {}", channelId, ex.getMessage());
                    return null;
                })
        );

        channelQueues.put(channelId, newFuture);
        return newFuture;
    }

    private String ensureBotIdentifier(String message) {
        return message.trim() + (message.endsWith(BOT_IDENTIFIER) ? "" : " " + BOT_IDENTIFIER);
    }

    private String formatTelexMessage(int progress, String message) {
        return String.format(
                "🔍 **Progress Update**\n" +
                        "━━━━━━━━━━━━━━━━━━\n" +
                        "📊 Progress: %d%%\n" +
                        "📢 Status: %s\n" +
                        "%s",
                progress, message, BOT_IDENTIFIER
        );
    }

    private String formatReport(String title, String content) {
        return String.format("**%s**\n\n%s\n%s", title, content, BOT_IDENTIFIER);
    }

    private void logDeliveryStatus(String type, String channelId, ResponseEntity<String> response) {
        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("✅ {} delivered to Telex (channel {}): {}", type, channelId, response.getBody());
        } else {
            log.error("❌ Failed to deliver {} to Telex (channel {}): {}", type, channelId, response.getBody());
        }
    }
}