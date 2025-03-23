package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexEvent;
import africa.siteanalysisagent.dto.TelexUserRequest;

public interface BotService {

        void handleEvent(TelexUserRequest userRequest);

    }

