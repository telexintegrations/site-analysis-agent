package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;

public interface LinkCrawlAndCategorizationService {

    public CategorizedLink categorizeLinks(Document document);

    public Map<String, List<String>> analyzeLinkIssues(String baseUrl, CategorizedLink links);
}
