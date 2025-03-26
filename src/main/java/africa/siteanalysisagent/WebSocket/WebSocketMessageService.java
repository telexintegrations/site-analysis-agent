//package africa.siteanalysisagent.WebSocket;
//
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//
//import java.io.IOException;
//import java.util.Collections;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Service
//@Slf4j
//public class WebSocketMessageService {
//    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
//    private final Object lock = new Object();
//
//    public void registerSession(String channelId, WebSocketSession session) {
//        synchronized (lock) {
//            sessions.put(channelId, session);
//            log.info("Registered session for channel: {}", channelId);
//        }
//    }
//
//    public void unregisterSession(String channelId) {
//        synchronized (lock) {
//            sessions.remove(channelId);
//            log.info("Unregistered session for channel: {}", channelId);
//        }
//    }
//
//    public void sendToChannel(String channelId, String message) {
//        synchronized (lock) {
//            WebSocketSession session = sessions.get(channelId);
//            if (session != null && session.isOpen()) {
//                try {
//                    session.sendMessage(new TextMessage(message));
//                    log.debug("Sent message to channel: {}", channelId);
//                } catch (IOException e) {
//                    log.error("Failed to send message to channel {}: {}", channelId, e.getMessage());
//                    sessions.remove(channelId);
//                }
//            } else {
//                log.warn("No active session for channel: {}", channelId);
//                sessions.remove(channelId);
//            }
//        }
//    }
//
//    public Map<String, WebSocketSession> getSessions() {
//        return Collections.unmodifiableMap(sessions);
//    }
//}