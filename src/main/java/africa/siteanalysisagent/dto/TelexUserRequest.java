package africa.siteanalysisagent.dto;

import africa.siteanalysisagent.model.Setting;

import java.util.List;

public record TelexUserRequest(
        String message,
        List<Setting> settings) {
}