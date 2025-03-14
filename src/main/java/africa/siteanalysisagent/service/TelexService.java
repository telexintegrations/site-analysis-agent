package africa.siteanalysisagent.service;

public interface TelexService {
    void notifyTelex(String message, String webhook_url);
}
