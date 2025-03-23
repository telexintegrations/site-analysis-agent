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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressTrackerImpl implements ProgressTracker {

    private final TelexService telexService;
    private final WebSocketMessageService webSocketMessageService;

    @Override
    public void sendProgress(String scanId, String channelId,  int progress, String message) {
        try {
            // Prepare the payload for WebSocket
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scanId", scanId);
            payload.put("progress", progress);
            payload.put("message", message);

            // Send the progress update via WebSocket
            webSocketMessageService.sendToUser(channelId, new ObjectMapper().writeValueAsString(payload));

            // Format the Telex message
            String telexMessage = "🔍 **Scanning Progress**\n"
                    + "------------------------------\n"
                    + "📊 **Progress:** `" + progress + "%`\n"
                    + "📢 **Status:** `" + message + "`\n"
                    + "------------------------------";

            // Send the progress update via Telex asynchronously
            CompletableFuture<ResponseEntity<String>> future = telexService.sendMessage(channelId, telexMessage);

            // Handle the response asynchronously
            future.thenAccept(response -> {
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Progress update sent successfully to Telex: {}", response.getBody());
                } else {
                    log.error("❌ Failed to send progress update to Telex: {}", response.getBody());
                }
            }).exceptionally(ex -> {
                log.error("❌ Error sending progress update to Telex: {}", ex.getMessage());
                return null;
            });

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendReport(String scanId, String channelId, String title, String reportContent) {
        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Cannot send Telex report: channel_id is missing.");
            return;
        }

        // Format the report
        String formattedReport = "**" + title + "**\n\n" + reportContent;

        // Send the report via WebSocket
        CompletableFuture<ResponseEntity<String>> future = telexService.sendMessage(channelId, formattedReport);

        future.thenAccept(response -> {
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Report sent successfully to Telex: {}", response.getBody());
                webSocketMessageService.sendToUser(channelId, formattedReport);
            } else {
                log.error("❌ Failed to send report to Telex: {}", response.getBody());
            }

    }).exceptionally(ex -> {
            log.error("❌ Error sending report to Telex: {}", ex.getMessage());
            return null;
        });
    }

    @Override
    public void sendAlert(String channelId, String alertMessage) {
        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Cannot send alert: channel_id is missing.");
            return;
        }

        // Send the alert via WebSocket
        webSocketMessageService.sendToUser(channelId,alertMessage);

        // Send the alert via Telex asynchronously
        CompletableFuture<ResponseEntity<String>> future = telexService.sendMessage(channelId, alertMessage);

        // Handle the response asynchronously
        future.thenAccept(response -> {
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Alert sent successfully to Telex: {}", response.getBody());
            } else {
                log.error("❌ Failed to send alert to Telex: {}", response.getBody());
            }
        }).exceptionally(ex -> {
            log.error("❌ Error sending alert to Telex: {}", ex.getMessage());
            return null;
        });
    }
}