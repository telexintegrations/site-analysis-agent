package africa.siteanalysisagent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.List;

public record TelexUserRequest(
        @JsonDeserialize(using = StringOrArrayDeserializer.class) String text,
        @JsonDeserialize(using = StringOrArrayDeserializer.class) String channelId,
        List<Setting> settings
) {
    @JsonCreator
    public TelexUserRequest(
            @JsonProperty("text") String text,
            @JsonProperty("channel_id") String channelId,  // Note: Matches JSON field name
            @JsonProperty("settings") List<Setting> settings
    ) {
        this.text = text;
        this.channelId = (channelId != null && !channelId.isBlank()) ? channelId : "default-channel-id";
        this.settings = settings != null ? settings : List.of();
    }

    // Custom deserializer to handle String or Array values
    public static class StringOrArrayDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.START_ARRAY) {
                // If the value is an array, take the first element
                List<?> list = p.readValueAs(List.class);
                return list.isEmpty() ? null : list.get(0).toString();
            }
            // Default case: treat as String
            return p.getValueAsString();
        }
    }
}