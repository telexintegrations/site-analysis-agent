package africa.siteanalysisagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Permission(
        @JsonProperty("always_online") boolean alwaysOnline,
        @JsonProperty("display_name") String displayName
) {
}
