package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unexpected fields
public record TelexUserRequest(
        String text,
        String channelId,
        List<Setting> settings) {

    @JsonCreator
    public TelexUserRequest(
            @JsonProperty("text") String text,
            @JsonProperty("channelId") String channelId,
            @JsonProperty("settings") List<Setting> settings
    ) {
        this.text = (text != null) ? text : "";  // ✅ Ensure text is never null
        this.channelId = (channelId != null && !channelId.isBlank()) ? channelId : "default-channel-id";
        this.settings = (settings != null) ? settings : List.of(); // ✅ Ensure settings is never null
    }
}
