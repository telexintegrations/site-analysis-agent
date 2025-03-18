package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Setting(
        String label,
        String type,
        String description,
        @JsonProperty("default") String defaultValue,
        boolean required
        ) {

}
