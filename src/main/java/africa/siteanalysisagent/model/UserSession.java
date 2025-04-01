package africa.siteanalysisagent.model;

import africa.siteanalysisagent.dto.SiteAnalysis;
import africa.siteanalysisagent.service.BrokenLinkAndDuplicateTracker;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserSession {
    private final String userId;
    private String currentUrl;
    private String channelId;
    private SiteAnalysis currentAnalysis;
    private SEOReport currentReport;
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private LocalDateTime lastActivity = LocalDateTime.now();
    private int analysisCount = 0;

    // Session configuration
    private static final long SESSION_TIMEOUT_MINUTES = 30;
    private static final int MAX_HISTORY = 20;

    public UserSession(String userId) {
        this.userId = userId;
    }

    public boolean hasActiveReport() {
        return currentReport != null && !isExpired();
    }

    public void updateAnalysisContext(String url, SiteAnalysis analysis, SEOReport report) {
        this.currentUrl = url;
        this.currentAnalysis = analysis;
        this.currentReport = report;
        this.analysisCount++;
        updateLastActivity();
    }

    public void addMessage(ChatMessage message) {
        chatHistory.add(message);
        pruneHistory();
        updateLastActivity();
    }

    public String getChatHistoryAsString() {
        StringBuilder sb = new StringBuilder();
        chatHistory.forEach(msg -> {
            if (msg.getUserMessage() != null) sb.append("User: ").append(msg.getUserMessage()).append("\n");
            if (msg.getBotResponse() != null) sb.append("Lynx: ").append(msg.getBotResponse()).append("\n");
        });
        return sb.toString();
    }

    public boolean isExpired() {
        return lastActivity.plusMinutes(SESSION_TIMEOUT_MINUTES).isBefore(LocalDateTime.now());
    }

    public void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    private void pruneHistory() {
        if (chatHistory.size() > MAX_HISTORY) {
            chatHistory.subList(0, chatHistory.size() - MAX_HISTORY).clear();
        }
    }

    // Corrected helper methods
    public int getTotalLinksFound() {
        return currentAnalysis != null ? currentAnalysis.getTotalLinksFound() : 0;
    }


    // Returns the count (int)
    public int getBrokenLinksCount() {
        return currentAnalysis != null ? currentAnalysis.getTotalBrokenLinks() : 0;
    }

    public SiteAnalysis getCurrentAnalysis() {
        return this.currentAnalysis;
    }

    public double getSeoScore() {
        return currentReport != null ? currentReport.getScore() : -1;
    }
}