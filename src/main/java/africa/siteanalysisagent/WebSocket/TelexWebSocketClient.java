package africa.siteanalysisagent.WebSocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TelexWebSocketClient extends TextWebSocketHandler {

    private WebSocketSession session;

    public void connect(){

        String telexWebSocketUrl = "wss://api.telex.im/centrifugo/connection/websocket";

        WebSocketConnectionManager connectionManager =new WebSocketConnectionManager(
                new StandardWebSocketClient(),
                this,
                telexWebSocketUrl
        );
        connectionManager.start();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session){
        this.session = session;
        System.out.println("Connected to Telex WebSocket API");

        // Subscribe to the desired topic or channel
        String subscribeMessage = "{\"type\": \"subscribe\", \"channel\": \"scan_updates\"}";
        sendMessage(subscribeMessage);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message){
        System.out.println("Received message from Telex: " + message.getPayload());

        // Process the incoming message (e.g., update the bot)
        processTelexMessage(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("Disconnected from Telex WebSocket API: " + status);
    }


    public void sendMessage(String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                System.err.println("Failed to send message: " + e.getMessage());
            }
        }
    }
    private void processTelexMessage(String message) {
        // Handle the incoming message from Telex
        // Example: Update the bot with real-time data
        System.out.println("Processing Telex message: " + message);
    }
}
