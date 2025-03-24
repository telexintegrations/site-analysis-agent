package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TelexUserRequest(
        String text,
        String channelId,
        String username,
        List<Setting> settings
) {
    @JsonCreator
    public TelexUserRequest(
            @JsonProperty("text") String text,  // Only String allowed
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("username") String username,
            @JsonProperty("settings") List<Setting> settings
    ) {
        this.text = cleanText(text);
        this.channelId = (channelId != null && !channelId.isBlank()) ? channelId : "default-channel-id";
        this.username = (username != null && !username.isBlank()) ? username : "unknown";
        this.settings = (settings != null) ? settings : List.of();
    }

    // Clean and validate text input
    private static String cleanText(String textInput) {
        if (textInput == null || textInput.isBlank()) {
            return "[No text]";
        }
        return cleanHtml(textInput);
    }

    // Remove HTML tags and trim whitespace
    private static String cleanHtml(String input) {
        return input.replaceAll("<[^>]*>", "").trim();
    }

    // Static factory method for backward compatibility
    public static TelexUserRequest fromRawData(String rawText, String channelId, List<Setting> settings) {
        return new TelexUserRequest(rawText, channelId, "unknown", settings);
    }
}