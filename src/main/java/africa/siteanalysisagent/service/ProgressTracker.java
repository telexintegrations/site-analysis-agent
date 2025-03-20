package africa.siteanalysisagent.service;

public interface ProgressTracker {

        void sendProgress(String scanId, String webhookUrl, int progress, String message);

        void sendReport(String scanId, String webhookUrl, String title, String reportContent);

        void sendAlert(String channelId, String alertMessage);
    }
