package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexEvent;

public interface BotService {

    void handleEvent(TelexEvent event);
}
