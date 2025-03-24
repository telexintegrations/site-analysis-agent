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
    public TelexUserRequest(@JsonProperty("text") String text,
                            @JsonProperty("channelId") String channelId,
                            @JsonProperty("settings") List<Setting> settings,
                            @JsonProperty("data") Map<String, Object> data) {
        this(
                text != null ? text : (data != null ? (String) data.get("text") : null),
                (channelId != null && !channelId.isBlank()) ? channelId : (data != null ? (String) data.get("channelId") : "default-channel-id"),
                settings != null ? settings : List.of()
        );
    }
}
