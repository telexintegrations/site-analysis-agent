package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import org.jsoup.nodes.Document;

public interface LinkCrawlAndCategorizationService {

    CategorizedLink categorizedLinkDto(Document document);

    void detectBrokenAndDuplicateLinks(String scanId, String channelId, CategorizedLink categorizedLinks);
}
