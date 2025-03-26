//package africa.siteanalysisagent.WebSocket;
//
//import africa.siteanalysisagent.dto.TelexUserRequest;
//import africa.siteanalysisagent.service.BotService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.client.ClientHttpRequestInterceptor;
//import org.springframework.http.client.InterceptingClientHttpRequestFactory;
//import org.springframework.http.client.SimpleClientHttpRequestFactory;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.*;
//import org.springframework.web.socket.client.WebSocketConnectionManager;
//import org.springframework.web.socket.client.standard.StandardWebSocketClient;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//
//import java.io.IOException;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.*;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class TelexWebSocketClient extends TextWebSocketHandler {
//
//    @Value("${telex.ws.token}")
//    private String authToken;
//
//    private static final long INITIAL_RECONNECT_DELAY_MS = 5000;  // Start with 5 seconds
//    private static final long MAX_RECONNECT_DELAY_MS = 60000;     // Max 60 seconds
//    private static final long HEARTBEAT_INTERVAL_MS = 20000;      // Send ping every 20 seconds
//    private static final long HEARTBEAT_TIMEOUT_MS = 30000;       // Wait max 30 seconds for pong
//    private static final String PING_MESSAGE = "{\"type\":\"ping\"}";
//    private static final String CONNECT_MESSAGE_TEMPLATE = """
//        {
//            "type": "connect",
//            "data": {
//                "token": "%s",
//                "name": "site-analysis-agent",
//                "version": "1.0"
//            }
//        }""";
//
//    private final WebSocketMessageService webSocketMessageService;
//    private final BotService botService;
//    private final ObjectMapper objectMapper;
//
//    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
//    private final Object connectionLock = new Object();
//    private WebSocketSession session;
//    private ScheduledFuture<?> heartbeatTask;
//    private volatile long lastHeartbeatResponse = 0;
//    private volatile boolean isConnected = false;
//    private volatile long currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS;
//
//    @PostConstruct
//    public void init() {
//        log.info("Initializing WebSocket client...");
//        connect();
//    }
//
//    private void connect() {
//        synchronized (connectionLock) {
//            if (isConnected) return;
//
//            log.info("🔌 Attempting WebSocket connection...");
//            try {
//                StandardWebSocketClient client = new StandardWebSocketClient();
//                client.setUserProperties(Collections.singletonMap("Authorization", "Bearer " + authToken));
//
//                WebSocketConnectionManager manager = new WebSocketConnectionManager(
//                        client,
//                        this,
//                        "wss://api.telex.im/centrifugo/connection/websocket"
//                );
//                manager.start();
//            } catch (Exception e) {
//                log.error("❌ Connection failed", e);
//                scheduleReconnect();
//            }
//        }
//    }
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) {
//        synchronized (connectionLock) {
//            this.session = session;
//            this.isConnected = true;
//            this.lastHeartbeatResponse = System.currentTimeMillis();
//            this.currentReconnectDelay = INITIAL_RECONNECT_DELAY_MS;
//
//            try {
//                // Send connection authentication message
//                String authMessage = """
//            {
//                "type": "connect",
//                "data": {
//                    "token": "%s",
//                    "format": "json",
//                    "name": "site-analysis-agent",
//                    "version": "1.0"
//                }
//            }""".formatted(authToken);
//
//                session.sendMessage(new TextMessage(authMessage));
//                log.info("✅ Sent authentication message to Telex");
//
//                // Start heartbeat
//                heartbeatTask = executor.scheduleAtFixedRate(
//                        this::checkHeartbeat,
//                        HEARTBEAT_INTERVAL_MS,
//                        HEARTBEAT_INTERVAL_MS,
//                        TimeUnit.MILLISECONDS
//                );
//
//                webSocketMessageService.registerSession("default", session);
//            } catch (Exception e) {
//                log.error("❌ Failed WebSocket handshake with Telex", e);
//                cleanupConnection();
//            }
//        }
//    }
//
//
//    private void checkHeartbeat() {
//        synchronized (connectionLock) {
//            if (!isConnected) return;
//
//            long timeSinceLastResponse = System.currentTimeMillis() - lastHeartbeatResponse;
//            if (timeSinceLastResponse > HEARTBEAT_TIMEOUT_MS) {
//                log.warn("⚠️ WebSocket heartbeat timeout ({}ms), reconnecting...", timeSinceLastResponse);
//                cleanupConnection();
//                scheduleReconnect();
//                return;
//            }
//
//            try {
//                if (session != null && session.isOpen()) {
//                    session.sendMessage(new TextMessage(PING_MESSAGE));
//                    log.trace("📤 Sent heartbeat ping");
//                }
//            } catch (IOException e) {
//                log.error("❌ Heartbeat send failed", e);
//                cleanupConnection();
//                scheduleReconnect();
//            }
//        }
//    }
//
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
//        String payload = message.getPayload();
//
//        try {
//            if (payload.contains("\"type\":\"pong\"")) {
//                synchronized (connectionLock) {
//                    lastHeartbeatResponse = System.currentTimeMillis();
//                    log.trace("✅ Pong received");
//                }
//                return;
//            }
//
//            TelexUserRequest userRequest = objectMapper.readValue(payload, TelexUserRequest.class);
//            if (userRequest.text() != null && !userRequest.text().isBlank()) {
//                botService.handleEvent(userRequest);
//            }
//        } catch (Exception e) {
//            log.error("❌ Error processing message: {}", payload, e);
//        }
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
//        synchronized (connectionLock) {
//            log.warn("🔴 Connection closed: {}", status);
//            cleanupConnection();
//            if (!CloseStatus.NORMAL.equals(status)) {
//                scheduleReconnect();
//            }
//        }
//    }
//
//    @Override
//    public void handleTransportError(WebSocketSession session, Throwable exception) {
//        synchronized (connectionLock) {
//            log.error("❌ Transport error", exception);
//            cleanupConnection();
//            scheduleReconnect();
//        }
//    }
//
//    private void cleanupConnection() {
//        synchronized (connectionLock) {
//            try {
//                if (heartbeatTask != null) {
//                    heartbeatTask.cancel(true);
//                    heartbeatTask = null;
//                }
//
//                if (session != null) {
//                    if (session.isOpen()) {
//                        session.close();
//                    }
//                    session = null;
//                }
//
//                isConnected = false;
//                webSocketMessageService.unregisterSession("default");
//            } catch (Exception e) {
//                log.error("❌ Cleanup error", e);
//            }
//        }
//    }
//
//    private void scheduleReconnect() {
//        if (!isConnected) {
//            long delay = Math.min(currentReconnectDelay, MAX_RECONNECT_DELAY_MS);
//            currentReconnectDelay = (long)(currentReconnectDelay * 1.5);
//
//            log.warn("🔄 Reconnecting in {}ms", delay);
//
//            if (currentReconnectDelay >= MAX_RECONNECT_DELAY_MS) {
//                log.error("❌ Telex keeps rejecting our connection. Check API changes.");
//                return; // Stop retrying if rejection persists
//            }
//
//            executor.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
//        } else {
//            log.warn("⚠️ Skipping reconnect - already connected.");
//        }
//    }
//
//
//    @PreDestroy
//    public void shutdown() {
//        executor.shutdownNow();
//        cleanupConnection();
//    }
//}
