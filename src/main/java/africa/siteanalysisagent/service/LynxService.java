package africa.siteanalysisagent.service;

import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.ChatResponse;

public interface LynxService {
    public ChatResponse processMessage(ChatMessage chatMessage);
    public ChatResponse chat(String userId, String userMessage);
    }

