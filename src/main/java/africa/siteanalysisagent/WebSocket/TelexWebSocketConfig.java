package africa.siteanalysisagent.WebSocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class TelexWebSocketConfig implements WebSocketConfigurer {

    private final TelexWebSocketClient telexWebSocketClient;

    public TelexWebSocketConfig(TelexWebSocketClient telexWebSocketClient) {
        this.telexWebSocketClient = telexWebSocketClient;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register the WebSocket handler for incoming connections (if needed)
        // Not required for connecting to Telex's WebSocket API
    }

    @Bean
    public WebSocketConnectionManager webSocketConnectionManager() {
        return new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                telexWebSocketClient,
                "wss://api.telex.im/centrifugo/connection/websocket" // Telex WebSocket URL
        );
    }
}