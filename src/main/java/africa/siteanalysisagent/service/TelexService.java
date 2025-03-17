package africa.siteanalysisagent.service;

import africa.siteanalysisagent.model.Setting;

import java.util.List;

public interface TelexService {
    void notifyTelex(String message, String webhookUrl);
}
