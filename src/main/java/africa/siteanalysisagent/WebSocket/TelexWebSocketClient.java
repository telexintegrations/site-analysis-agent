package africa.siteanalysisagent.WebSocket;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.service.BotService;
import africa.siteanalysisagent.service.ProgressTracker;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelexWebSocketClient extends TextWebSocketHandler {

    private final WebSocketMessageService webSocketMessageService;
    private final BotService botService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean isConnected = false;
    private volatile WebSocketSession session;
    private final Object lock = new Object(); // Thread-safe lock

    @PostConstruct
    public void init() {
        log.info("🚀 Starting WebSocket connection...");
        connect();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        synchronized (lock) {
            this.session = session;
            isConnected = true;
        }
        log.info("✅ Successfully connected to Telex WebSocket: {}", session.getId());

        // Start Keep-Alive Pings Every 30 Seconds
        scheduler.scheduleAtFixedRate(() -> sendPing(), 30, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.info("📩 Received WebSocket message: {}", message.getPayload());

        try {
            TelexUserRequest userRequest = objectMapper.readValue(payload, TelexUserRequest.class);
            String channelId = userRequest.channelId();

            if (channelId != null && !channelId.isEmpty()) {
                webSocketMessageService.registerSession(channelId, session);
                log.info("🔗 Linked WebSocket session to channelId: {}", channelId);
            }

            botService.handleEvent(userRequest);
        } catch (Exception e) {
            log.error("❌ Failed to process WebSocket message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("⚠️ WebSocket disconnected: {} - Reason: {}", session.getId(), status.getReason());

        synchronized (lock) {
            isConnected = false;
            this.session = null;
        }

        String channelId = findChannelIdBySession(session);
        if (channelId != null) {
            webSocketMessageService.unregisterSession(channelId);
            log.info("🔌 Unregistered WebSocket session for channelId: {}", channelId);
        }

        // If NOT a normal closure, retry connection
        if (!status.equals(CloseStatus.NORMAL)) {
            retryConnection();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("❌ WebSocket transport error: {}", exception.getMessage());

        synchronized (lock) {
            isConnected = false;
            this.session = null;
        }

        retryConnection();
    }

    public void connect() {
        synchronized (lock) {
            if (isConnected) {
                log.warn("⚠️ WebSocket already connected, skipping reconnect...");
                return;
            }
        }

        String telexWebSocketUrl = "wss://api.telex.im/centrifugo/connection/websocket";
        log.info("🔌 Connecting to Telex WebSocket at {}", telexWebSocketUrl);

        try {
            WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
                    new StandardWebSocketClient(),
                    this,
                    telexWebSocketUrl
            );

            connectionManager.start();
            synchronized (lock) {
                isConnected = true;
            }
            log.info("✅ WebSocket connection initialized.");
        } catch (Exception e) {
            log.error("❌ Failed to initialize WebSocket connection: {}", e.getMessage());
            retryConnection();
        }
    }

    private void sendPing() {
        synchronized (lock) {
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage("{\"type\": \"ping\"}"));
                    log.info("📡 Sent keep-alive ping to Telex WebSocket.");
                } catch (IOException e) {
                    log.error("❌ Failed to send keep-alive ping: {}", e.getMessage());
                    retryConnection();
                }
            }
        }
    }

    private void retryConnection() {
        scheduler.schedule(() -> {
            log.info("🔄 Attempting WebSocket reconnection...");
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    private String findChannelIdBySession(WebSocketSession session) {
        return webSocketMessageService.getSessions()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
