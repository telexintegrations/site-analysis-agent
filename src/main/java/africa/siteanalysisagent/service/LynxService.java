package africa.siteanalysisagent.service;

import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.ChatResponse;
import africa.siteanalysisagent.model.UserSession;

public interface LynxService {
    // Core messaging functionality
    ChatResponse processMessage(ChatMessage chatMessage);
    ChatResponse chat(String userId, String userMessage);

    // Analysis methods
    ChatResponse processAnalysisRequest(String userId, String message, String url);
    ChatResponse processAnalysisRequest(String userId, String message, String url, UserSession session);

    // Scheduling functionality
    ChatResponse handleScheduleRequest(String userId, String message, String url, UserSession session);

    ChatResponse listScheduledScans(String userId);
    ChatResponse cancelScheduledScan(String userId, String message);
    public ChatResponse handleAnalysisRequest(String userId, String url);
    public ChatResponse handleScheduleRequest(String userId, String url, String interval);

}

