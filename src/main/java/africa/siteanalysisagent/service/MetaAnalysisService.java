package africa.siteanalysisagent.service;

import java.util.function.Consumer;

public interface MetaAnalysisService {
    boolean isSingleUrl (String url);

    void generateSeoReport(String url, String scanId, String channelId, Consumer<String> callback);

    String getOptimizedMetags(String channelId);

    void clearOptimizedMetags(String channelId);
}
