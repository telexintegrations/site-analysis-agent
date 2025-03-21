package africa.siteanalysisagent.service;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;

public interface MetaAnalysisService {
//    boolean isSingleUrl (String url);

    String generateSeoReport(String url,String scanId, String channelId);
}
