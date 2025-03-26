package africa.siteanalysisagent.service;

import java.util.concurrent.CompletableFuture;

public interface ProgressTracker {

    public CompletableFuture<Void> sendProgress(String scanId, String channelId, int progress, String message);

    public CompletableFuture<Void> sendReport(String scanId, String channelId, String title, String reportContent);

    public CompletableFuture<Void> sendAlert(String channelId, String alertMessage);
}
