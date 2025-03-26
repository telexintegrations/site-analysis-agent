package africa.siteanalysisagent.service;

import africa.siteanalysisagent.WebSocket.WebSocketMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
    private final WebSocketMessageService webSocketMessageService;
    private final ObjectMapper objectMapper;

    // Track last message future per channel
    private final Map<String, CompletableFuture<Void>> channelQueues = new ConcurrentHashMap<>();

    // Global sequence generator for all channels
    private final AtomicLong globalSequence = new AtomicLong(0);

    private static final String BOT_IDENTIFIER = "#bot_message";
    private static final Executor messageExecutor = Executors.newSingleThreadExecutor();

    @Override
    public CompletableFuture<Void> sendProgress(String scanId, String channelId, int progress, String message) {
        return enqueueMessage(channelId, () -> {
            try {
                // Prepare payload with global sequence number
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("scanId", scanId);
                payload.put("progress", progress);
                payload.put("message", ensureBotIdentifier(message));
                payload.put("sequence", globalSequence.getAndIncrement());
                payload.put("timestamp", System.currentTimeMillis());

                // Serialize payload
                String jsonPayload = objectMapper.writeValueAsString(payload);

                // Send to both transports
                return CompletableFuture.allOf(
                        sendWebSocketMessage(channelId, jsonPayload),
                        sendTelexMessage(channelId, formatTelexProgress(progress, message))
                );
            } catch (JsonProcessingException e) {
                log.error("JSON serialization failed for progress update", e);
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendReport(String scanId, String channelId, String title, String reportContent) {
        return enqueueMessage(channelId, () -> {
            String formattedContent = formatReport(title, reportContent);
            return CompletableFuture.allOf(
                    sendWebSocketMessage(channelId, formattedContent),
                    sendTelexMessage(channelId, formattedContent));
        });
    }

    @Override
    public CompletableFuture<Void> sendAlert(String channelId, String alertMessage) {
        return enqueueMessage(channelId, () -> {
            String formattedMessage = ensureBotIdentifier(alertMessage);
            return CompletableFuture.allOf(
                    sendWebSocketMessage(channelId, formattedMessage),
                    sendTelexMessage(channelId, formattedMessage));
        });
    }

    // ========== PRIVATE HELPERS ========== //

    private CompletableFuture<Void> enqueueMessage(String channelId, Supplier<CompletableFuture<Void>> messageSupplier) {
        // Get or create the queue tail for this channel
        CompletableFuture<Void> previousFuture = channelQueues.computeIfAbsent(
                channelId,
                k -> CompletableFuture.completedFuture(null)
        );

        // Chain the new message
        CompletableFuture<Void> newFuture = previousFuture
                .thenComposeAsync(ignored -> messageSupplier.get(), messageExecutor)
                .exceptionally(ex -> {
                    log.error("Message failed for channel {}: {}", channelId, ex.getMessage());
                    return null; // Continue queue despite failures
                });

        // Update the channel queue
        channelQueues.put(channelId, newFuture);
        return newFuture;
    }

    private CompletableFuture<Void> sendWebSocketMessage(String channelId, String message) {
        return CompletableFuture.runAsync(() -> {
            webSocketMessageService.sendToUser(channelId, message);
        }, messageExecutor);
    }

    private CompletableFuture<Void> sendTelexMessage(String channelId, String message) {
        return telexService.sendMessage(channelId, message)
                .thenAcceptAsync(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.debug("Message delivered to Telex for channel {}", channelId);
                    } else {
                        log.warn("Telex delivery failed for channel {}: {}", channelId, response.getBody());
                    }
                }, messageExecutor);
    }

    private String ensureBotIdentifier(String message) {
        return message.trim() + (message.endsWith(BOT_IDENTIFIER) ? "" : " " + BOT_IDENTIFIER);
    }

    private String formatTelexProgress(int progress, String message) {
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
        return String.format(
                "**%s**\n\n%s\n%s",
                title, content, BOT_IDENTIFIER
        );
    }
}