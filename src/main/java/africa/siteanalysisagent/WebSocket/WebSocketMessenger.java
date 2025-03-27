package africa.siteanalysisagent.WebSocket;

import africa.siteanalysisagent.service.TelexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketMessenger {
    private static final Logger log = LoggerFactory.getLogger(WebSocketMessenger.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final TelexService telexService;
    private final Map<String, Boolean> connectionStatus = new ConcurrentHashMap<>();


    public WebSocketMessenger(SimpMessagingTemplate messagingTemplate, TelexService telexService) {
        this.messagingTemplate = messagingTemplate;
        this.telexService = telexService;
    }

    public CompletableFuture<Void> sendToTelex(String channelId, String message){
        return CompletableFuture.runAsync(() -> {
            int attempts = 0;
            final int maxAttempts = 3;

            while (attempts < maxAttempts) {
                try {
                    // 1. Verify connection
                    if (!isConnectionActive(channelId)) {
                        establishConnection(channelId);
                    }

                    // 2. Send via WebSocket
                    String destination = "/app/telex/" + channelId;
                    messagingTemplate.convertAndSend(destination, message);

                    // 3. Confirm delivery
                    log.info("Message sent to Telex via WebSocket: {}", message);
                    break;
                }catch (Exception e){
                    attempts++;
                    if (attempts >= maxAttempts){
                        // Fallback to direct HTTP if WebSocket fails
                        telexService.sendMessage(channelId, message)
                                .exceptionally(ex -> {
                                    log.error("Final fallback failed for channel {}: {}",
                                            channelId, ex.getMessage());
                                    return null;
                                });
                    }else {
                        try {
                            Thread.sleep(500 + attempts);
                        }catch (InterruptedException ie){
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        });
    }

    private boolean isConnectionActive(String channelId){
        return connectionStatus.getOrDefault(channelId, false);
    }

    private void establishConnection(String channelId){
        try {
            // Simple ping message to establish connection
            messagingTemplate.convertAndSend("/app/telex" + channelId,
                    Map.of("type", "ping"));
            connectionStatus.put(channelId, true);
        }catch (Exception e){
            connectionStatus.put(channelId, false);
            throw new RuntimeException("Failed to establish websocket connection", e);
        }
    }
}
