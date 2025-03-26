//package africa.siteanalysisagent.WebSocket;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.socket.client.WebSocketConnectionManager;
//import org.springframework.web.socket.client.standard.StandardWebSocketClient;
//import org.springframework.web.socket.config.annotation.EnableWebSocket;
//import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
//import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
//import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@Configuration
//@EnableWebSocket
//@RequiredArgsConstructor
//public class TelexWebSocketConfig implements WebSocketConfigurer {
//
//    private final TelexWebSocketClient telexWebSocketClient;
//
//    @Bean
//    public ServletServerContainerFactoryBean createWebSocketContainer() {
//        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
//        container.setMaxSessionIdleTimeout(300000L); // 5 minutes
//        container.setMaxTextMessageBufferSize(8192);
//        return container;
//    }
//
//    @Bean
//    public WebSocketConnectionManager webSocketConnectionManager(
//            TelexWebSocketClient webSocketClient,
//            @Value("${telex.ws.url}") String wsUrl,
//            @Value("${telex.ws.token}") String authToken) {
//
//        StandardWebSocketClient client = new StandardWebSocketClient();
//        Map<String, Object> headers = client.getUserProperties();
//
//        // Add the required authorization header
//        headers.put("org.apache.tomcat.websocket.WS_AUTHENTICATION", "Bearer " + authToken);
//        headers.put("Sec-WebSocket-Protocol", "json"); // Ensure correct WebSocket format
//
//        WebSocketConnectionManager manager = new WebSocketConnectionManager(
//                client,
//                webSocketClient,
//                wsUrl
//        );
//
//        manager.setAutoStartup(true);
//        return manager;
//    }
//
//
//
//
//    @Override
//    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//        // Register the WebSocket handler for incoming connections (if needed)
//        // Not required for connecting to Telex's WebSocket API
//    }
//
//}