package africa.siteanalysisagent.service;

public interface MetaAnalysisService {
    boolean isSingleUrl (String url);

    void generateSeoReport(String url, String scanId, String channelId);

    String getOptimizedMetags(String channelId);

    void clearOptimizedMetags(String channelId);
}
