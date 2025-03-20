package africa.siteanalysisagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAnalysisServiceImpl implements MetaAnalysisService {

    private final TelexService telexService;

    private static final int TIMEOUT = 10000; // 10 seconds


    @Override
    public boolean isSingleUrl(String url) {
        // validation to ensure url is not a homepage
        if (url == null || !url.matches(".*/.*")) {
            log.warn("Invalid URL detected: {}. Please input a single page URL.", url);
            return false;
        }
        return true;
    }

    @Override
    public Document scrape(String url) throws IOException {
        if (!isSingleUrl(url)) {
            throw new IllegalArgumentException("Invalid URL. please input a single page URL");
        }
        return Jsoup.connect(url).timeout(TIMEOUT).get();
    }

    @Override
    public List<String> checkMetaTags(Document document) {
        List<String> metaTagIssues = new ArrayList<>();

        // check title tag
        Element titletag = document.selectFirst("title");
        if (titletag == null || titletag.text().isBlank()) {
            metaTagIssues.add("Missing title tag\n");
        } else {
            metaTagIssues.add("Title tag: " + titletag.text() + "\n");
        }

        // check meta description
        Element metaDescription = document.selectFirst("meta[name=description]");
        if (metaDescription == null || metaDescription.attr("content").isBlank()) {
            metaTagIssues.add("Missing meta description\n");
        } else {
            metaTagIssues.add("Meta Description: " + metaDescription.attr("content") + "\n");
        }

        // check meta keywords
        Element metaKeyword = document.selectFirst("meta[name=keyword]");
        if (metaKeyword == null || metaKeyword.attr("content").isBlank()) {
            metaTagIssues.add("Missing meta keywords\n");
        } else {
            metaTagIssues.add("Meta Keywords: " + metaKeyword.attr("content") + "\n");
        }
        return metaTagIssues;
    }

    @Override
    public String generateSeoReport(String url,String scanId, String webhookurl) {
        try {
            Document document = scrape(url);
            List<String> metaTagIssues = checkMetaTags(document);

            StringBuilder report = new StringBuilder("SEO Analysis Report for: " + url + "\n\n");
            if (metaTagIssues.isEmpty()) {
                report.append("All essential meta tags are present");
            } else {
                metaTagIssues.forEach(issue -> report.append("- ").append(issue).append('\n'));
            }

            telexService.notifyTelex(report.toString(),webhookurl);
            return report.toString();
        } catch (IOException | IllegalArgumentException e) {
            return "Failed to generate SEO report: " + e.getMessage();
        }
    }

    @Override
    public boolean isHomepage(String url) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isHomepage'");
    }
}
