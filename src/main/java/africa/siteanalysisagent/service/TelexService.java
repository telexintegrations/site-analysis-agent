package africa.siteanalysisagent.service;


import africa.siteanalysisagent.dto.Button;

import java.util.List;

public interface TelexService {

    void sendMessage (String channelId, String message);
    void sendInteractiveMessage(String webhookUrl, String message, List<Button> buttons);
    void notifyTelex(String message, String webhookUrl);
}
