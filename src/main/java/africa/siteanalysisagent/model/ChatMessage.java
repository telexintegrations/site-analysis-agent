package africa.siteanalysisagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ChatMessage {
    private String userId;               // User identifier (unchanged)
    private String userMessage;          // Renamed from 'message' for clarity
    private String botResponse;          // Renamed from 'response' for clarity
    private LocalDateTime timestamp;     // Unchanged
    private String intentType;           // NEW: Stores the detected intent (NEW_ANALYSIS, REPORT_QUESTION, etc.)
    private String contextUrl;           // NEW: URL being discussed (if applicable)
    private List<String> messageTags;    // NEW: For categorizing messages (e.g., ["seo", "technical", "broken-links"])



    // Keeps backward compatibility with existing code
    public ChatMessage(String userId, String userMessage, String botResponse, LocalDateTime timestamp) {
        this(userId, userMessage, botResponse, timestamp, null, null, null);
    }

    public String getUserMessage() {
        return this.userMessage;
    }

    public String getBotResponse() {
        return this.botResponse;
    }


}