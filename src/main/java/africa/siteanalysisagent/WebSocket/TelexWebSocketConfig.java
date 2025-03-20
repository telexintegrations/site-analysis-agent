package africa.siteanalysisagent.WebSocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class TelexWebSocketConfig implements WebSocketConfigurer {

    private final TelexWebSocketClient telexWebSocketClient;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

    }
    public void startWebSocketConnection(){
        telexWebSocketClient.connect();
    }
}
