package africa.siteanalysisagent.service;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;

public interface MetaAnalysisService {
    boolean isSingleUrl (String url);

    Document scrape(String url) throws IOException;

    List<String> checkMetaTags(Document document);

    String generateSeoReport(String url);

    boolean isHomepage(String url);
}
