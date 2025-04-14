package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record TelexUserRequest(
        String text,
        String channelId,
        String userId,
        String webhookToken,
        List<Button> buttons

) {
    @JsonCreator
    public TelexUserRequest(
            @JsonProperty("text") String text,  // Only String allowed
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("user_id") String userId,
            @JsonProperty("webhook_token") String webhookToken,
            @JsonProperty("buttons") List<Button> buttons
    ) {
        this.text = sanitizeText(text);
        this.channelId = validateChannelId(channelId);
        this.userId = (userId != null) ? userId : "user-" + channelId;
        this.webhookToken = (webhookToken != null) ? webhookToken : "";
        this.buttons = (buttons != null) ? List.copyOf(buttons) : List.of();
    }

    // Simplified constructor for common use cases
    public TelexUserRequest(String text, String channelId) {
        this(text, channelId,  null, null, null);
    }

    private static String sanitizeText(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Message text cannot be null or blank");
        }
        // Remove HTML tags and trim whitespace
        return input.replaceAll("<[^>]*>", "").trim();
    }

    private static String validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("Channel ID cannot be null or blank");
        }
        return channelId;
    }



    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private String channelId;
        private String userId;
        private String webhookToken;
        private Map<String, String> metadata;
        private List<Button> buttons;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder channelId(String channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder webhookToken(String webhookToken) {
            this.webhookToken = webhookToken;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder buttons(List<Button> buttons) {
            this.buttons = buttons;
            return this;
        }

        public TelexUserRequest build() {
            return new TelexUserRequest(text, channelId, userId, webhookToken, buttons);
        }
    }

}