package africa.siteanalysisagent.dto;

import java.util.List;

public record TelexUserRequest(
        String message,
        String webhook,
        List<Setting> settings) {
} 