package africa.siteanalysisagent.service;


import java.util.List;

public interface TelexService {
    void notifyTelex(String message, String webhookUrl);
}
