package africa.siteanalysisagent.dto;

import java.util.List;

public record TelexUserRequest(
        String message,
        List<Setting> settings) {
} 