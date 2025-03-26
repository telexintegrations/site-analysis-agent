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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressTrackerImpl implements ProgressTracker {

    private final TelexService telexService;
    private final WebSocketMessageService webSocketMessageService;
    private final Map<String, AtomicInteger> messageSequences = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> lastMessageFutures = new ConcurrentHashMap<>();

    private static final String BOT_IDENTIFIER = "#bot_message";

    @Override
    public CompletableFuture<Void> sendProgress(String scanId, String channelId, int progress, String message) {
        // Get or create sequence counter for this channel
        AtomicInteger sequence = messageSequences.computeIfAbsent(
                channelId, k -> new AtomicInteger(0));

        // Chain to the previous message's future
        CompletableFuture<Void> previousFuture = lastMessageFutures.getOrDefault(channelId,
                CompletableFuture.completedFuture(null));

        CompletableFuture<Void> currentFuture = previousFuture.thenCompose(ignored -> {
            try {
                // Prepare payload with sequence number
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("scanId", scanId);
                payload.put("progress", progress);
                payload.put("message", message);
                payload.put("sequence", sequence.getAndIncrement());

                // Send WebSocket immediately
                webSocketMessageService.sendToUser(
                        channelId,
                        new ObjectMapper().writeValueAsString(payload));

                // Format and send Telex message
                String telexMessage = "🔍 **Scanning Progress**\n" +
                        "------------------------------\n" +
                        "📊 **Progress:** `" + progress + "%`\n" +
                        "📢 **Status:** `" + message + "`\n" +
                        "------------------------------";

                return telexService.sendMessage(channelId, telexMessage)
                        .thenAccept(response -> {
                            if (response.getStatusCode().is2xxSuccessful()) {
                                log.info("✅ Progress update sent successfully to Telex: {}", response.getBody());
                            } else {
                                log.error("❌ Failed to send progress update to Telex: {}", response.getBody());
                            }
                        });
            } catch (JsonProcessingException e) {
                log.error("❌ Error creating progress payload: {}", e.getMessage());
                return CompletableFuture.completedFuture(null);
            }
        });

        // Update the last message future for this channel
        lastMessageFutures.put(channelId, currentFuture);
        return currentFuture;
    }

    @Override
    public CompletableFuture<Void> sendReport(String scanId, String channelId, String title, String reportContent) {
        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Cannot send Telex report: channel_id is missing.");
            return CompletableFuture.completedFuture(null);
        }

        // Format the report
        String formattedReport = "**" + title + "**\n\n" + reportContent;

        // Chain to the previous message's future
        CompletableFuture<Void> previousFuture = lastMessageFutures.getOrDefault(channelId,
                CompletableFuture.completedFuture(null));

        CompletableFuture<Void> currentFuture = previousFuture.thenCompose(ignored -> {
            // Send the report via WebSocket
            webSocketMessageService.sendToUser(channelId, formattedReport);

            return telexService.sendMessage(channelId, formattedReport)
                    .thenAccept(response -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            log.info("✅ Report sent successfully to Telex: {}", response.getBody());
                        } else {
                            log.error("❌ Failed to send report to Telex: {}", response.getBody());
                        }
                    });
        });

        lastMessageFutures.put(channelId, currentFuture);
        return currentFuture;
    }

    @Override
    public CompletableFuture<Void> sendAlert(String channelId, String alertMessage) {
        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Cannot send alert: channel_id is missing.");
            return CompletableFuture.completedFuture(null);
        }

        // Chain to the previous message's future
        CompletableFuture<Void> previousFuture = lastMessageFutures.getOrDefault(channelId,
                CompletableFuture.completedFuture(null));



        CompletableFuture<Void> currentFuture = previousFuture.thenCompose(ignored -> {


            // Ensure message ends with bot identifier
            String formattedMessage = alertMessage.trim();
            if (!formattedMessage.endsWith(BOT_IDENTIFIER)) {
                formattedMessage += " " + BOT_IDENTIFIER;
            }
            // Send the alert via WebSocket
            webSocketMessageService.sendToUser(channelId, formattedMessage);

            return telexService.sendMessage(channelId, formattedMessage)
                    .thenAccept(response -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            log.info("✅ Alert sent successfully to Telex: {}", response.getBody());
                        } else {
                            log.error("❌ Failed to send alert to Telex: {}", response.getBody());
                        }
                    });
        });

        lastMessageFutures.put(channelId, currentFuture);
        return currentFuture;
    }
}