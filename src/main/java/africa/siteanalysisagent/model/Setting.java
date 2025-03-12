package africa.siteanalysisagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Setting(
        String label,
        String type,
        boolean required,
        @JsonProperty("default") String settingDefault) {

}
