package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexEvent;
import africa.siteanalysisagent.dto.TelexUserRequest;

public interface BotService {

        /**
         * Handles user interaction events from Telex.
         * @param userRequest The request containing user input and settings.
         */
        void handleEvent(TelexUserRequest userRequest);

    }

