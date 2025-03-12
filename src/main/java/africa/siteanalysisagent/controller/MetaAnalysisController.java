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

    private MetaAnalysisService metaAnalysisService;
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
                    System.out.println("Meta Tag Issues: " + metaTagIssues);
                }

                telexService.notifyTelex(
                        "Site analysis for" +
                                url + "successful"
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
            return "Invalid URL. please input a single page URL";
        }
    }

}
