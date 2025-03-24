package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
        this.text = text;
        this.channelId = (channelId != null && !channelId.isBlank()) ? channelId : "default-channel-id";
        this.settings = settings != null ? settings : List.of(); // Ensure settings is never null
    }

}






