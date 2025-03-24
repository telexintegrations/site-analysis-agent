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
            @JsonProperty("text") Object textObj,  // Accepts String, List, or null
            @JsonProperty("channel_id") String channelId,
            @JsonProperty("settings") List<Setting> settings
    ) {
        // Delegate to the canonical constructor with processed values
        this(
                // Handle text: convert Array to String or default if null
                (textObj instanceof List)
                        ? ((List<?>) textObj).get(0).toString()
                        : (textObj != null) ? textObj.toString() : "[No text]",

                // Handle channelId (default if null/blank)
                (channelId == null || channelId.isBlank())
                        ? "default-channel-id"
                        : channelId,

                // Handle settings (empty list if null)
                settings != null ? settings : List.of()
        );
    }
}