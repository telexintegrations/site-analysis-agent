package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TelexUserRequest(
        String text,
        String channelId,
        List<Setting> settings
) {
    @JsonCreator
    public TelexUserRequest(
            @JsonProperty("text") String text,
            @JsonProperty("channelId") String channelId,
            @JsonProperty("settings") List<Setting> settings
    ) {
        this.text = cleanHtml(text);
        this.channelId = (channelId != null && !channelId.isBlank()) ? channelId : "default-channel-id";
        this.settings = (settings != null) ? settings : List.of(); // Ensure settings is never null
    }

    // ✅ Static factory method to handle raw input safely
    public static TelexUserRequest fromRawData(Object rawText, String channelId, List<Setting> settings) {
        String textValue = (rawText instanceof String && !((String) rawText).isBlank()) ? cleanHtml((String) rawText) : "[No text]";
        return new TelexUserRequest(textValue, channelId, settings);
    }

    // ✅ Remove HTML tags from text
    private static String cleanHtml(String input) {
        if (input == null || input.isBlank()) return "[No text]";
        return input.replaceAll("<[^>]*>", "").trim(); // Remove HTML tags
    }
}
