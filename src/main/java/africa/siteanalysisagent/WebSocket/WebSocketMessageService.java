//package africa.siteanalysisagent.WebSocket;
//
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//
//import java.io.IOException;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Getter
//@Service
//@Slf4j
//public class WebSocketMessageService {
//
//    // ✅ Add this method to return all sessions
//    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
//
//    public void registerSession(String channelIdId, WebSocketSession session) {
//        sessions.put(channelIdId, session);
//    }
//
//    public void unregisterSession(String channelId) {
//        sessions.remove(channelId);
//    }
//
//    public void sendMessage(WebSocketSession session, String message) {
//        if (session != null && session.isOpen()) {
//            try {
//                session.sendMessage(new TextMessage(message));
//            } catch (IOException e) {
//                log.error("❌ Failed to send WebSocket message: {}", e.getMessage());
//            }
//        }
//    }
//
//    public void sendToUser(String channelId, String message) {
//        WebSocketSession session = sessions.get(channelId);
//        if (session != null && session.isOpen()) {
//            try {
//                session.sendMessage(new TextMessage(message));
//            } catch (IOException e) {
//                log.error("❌ Failed to send WebSocket message: {}", e.getMessage());
//            }
//        } else {
//            log.warn("⚠️ WebSocket session not found or closed for session ID: {}", channelId);
//        }
//    }
//
//}
//
//
//
//
//
//
