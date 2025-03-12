package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.UrlRequest;
import africa.siteanalysisagent.service.MetaAnalysisService;
import africa.siteanalysisagent.service.TelexService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/meta-analysis")
public class MetaAnalysisController {

    @Autowired
    private MetaAnalysisService metaAnalysisService;
    @Autowired
    private TelexService telexService;

    @PostMapping("/scrape")
    public String scrapeAndGenerateUrlReport(@RequestBody UrlRequest entity) {
        String url = entity.getUrl();

        try {

            Document document = null;
            document = metaAnalysisService.scrape(url);
            String seoReport = metaAnalysisService.generateSeoReport(url);
            if (document != null) {
                List<String> metaTagIssues = metaAnalysisService.checkMetaTags(document);

                if (metaTagIssues.size() > 0) {
                    System.out.println("Meta Tags Found: " + metaTagIssues);
                }

                telexService.notifyTelex(
                        "Site analysis for " +
                                url + "successful\n"
                                + "\nSEO Report:"
                                + "\n"
                                + seoReport
                                + "\nMeta Tag Issues:"
                                + "\n" + metaTagIssues);
                return "Scraping successful";
            } else {
                return "Unable to scrape" + url;
            }

        } catch (Exception e) {
            return "Failed to scrape" + url + e.getLocalizedMessage();
        }
    }

}
