package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TelexUserRequest(
        String message,
        List<Setting> settings) {
    @JsonCreator
    public TelexUserRequest(
            @JsonProperty("message") String message,
            @JsonProperty("settings") List<Setting> settings
    ) {
        this.message = message;
        this.settings = settings != null ? settings : List.of(); // Ensure settings is never null
    }
}