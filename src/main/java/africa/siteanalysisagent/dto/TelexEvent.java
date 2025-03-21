package africa.siteanalysisagent.dto;

public class TelexEvent {
    private String type; // Event type (e.g., "message", "interactive_message")
    private String userId; // ID of the user who triggered the event
    private String text; // Message text or button value
    private String action; // Action ID (for button clicks)
    private String channelId; // Channel or DM ID where the event occurred
    private String threadId; // Add thread ID field

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    @Override
    public String toString(){
        return "TelexEvent{" +
                "type='" + type + '\'' +
                ", userId='" + userId + '\'' +
                ", text='" + text + '\'' +
                ", action='" + action + '\'' +
                ", channelId='" + channelId + '\'' +
                '}';
    }
}

