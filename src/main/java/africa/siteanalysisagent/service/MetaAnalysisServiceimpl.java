package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAnalysisServiceimpl implements MetaAnalysisService {

    private final TelexService telexService;
    private final ProgressTracker progressTracker;
    private final LinkCrawlAndCategorizationService linkCrawlAndCategory;
    private final BrokenLinkAndDuplicateTracker brokenLinkAndDuplicateTracker;


    private static final int TIMEOUT = 10000; // 10 seconds


//    @Override
//    public boolean isSingleUrl(String url) {
//        // validation to ensure url is not a homepage
//        if (url == null || !url.matches(".*/.*")) {
//            log.warn("Invalid URL detected: {}. Please input a single page URL.", url);
//            return false;
//        }
//        return true;
//    }



    @Override
    public String generateSeoReport(String url, String scanId, String channelId) {
        try {
            // Directly start the SEO Meta Tag Scan
            progressTracker.sendProgress(scanId, channelId, 10, "🔄 Starting SEO Meta Tag Scan...");
            Thread.sleep(3000);

            Document document = scrape(url);

            progressTracker.sendProgress(scanId, channelId, 40, "🏷️ Extracting Meta Tags...");
            Thread.sleep(3000);
            MetaTagExtractor metaTagExtractor = new MetaTagExtractor(document);
            Map<String, List<String>> metaTags = metaTagExtractor.extractMetaTags();

            progressTracker.sendProgress(scanId, channelId, 80, "📊 Generating SEO Meta Tag Report...");
            Thread.sleep(3000);
            String seoMetaTagReport = generateMetaTagReport(url, metaTags);

            progressTracker.sendProgress(scanId, channelId, 100, "✅ SEO Meta Tag Scan Completed!");
            progressTracker.sendReport(scanId, channelId, "🏷️ **SEO Meta Tag Report**", seoMetaTagReport);
            Thread.sleep(5000); // Wait before switching

            // Directly start the Categorized Link Scan
            progressTracker.sendProgress(scanId, channelId, 10, "🔄 Starting Categorized Link Scan...");
            Thread.sleep(3000);

            progressTracker.sendProgress(scanId, channelId, 40, "🔗 Scanning Links...");
            Thread.sleep(3000);
            CategorizedLink categorizedLinks = linkCrawlAndCategory.categorizedLinkDto(document);

            progressTracker.sendProgress(scanId, channelId, 80, "📊 Generating Categorized Link Report...");
            Thread.sleep(3000);
            String categorizedLinkReport = generateCategorizedLinkReport(url, categorizedLinks);

            progressTracker.sendProgress(scanId, channelId, 100, "✅ Categorized Link Scan Completed!");
            progressTracker.sendReport(scanId, channelId, "🔗 **Categorized Link Report**", categorizedLinkReport);
            Thread.sleep(5000);

            // Directly start the Broken & Duplicate Link Scan
            progressTracker.sendProgress(scanId, channelId, 10, "🔄 Starting Broken & Duplicate Links Scan...");
            Thread.sleep(3000);

            linkCrawlAndCategory.detectBrokenAndDuplicateLinks(scanId, channelId, categorizedLinks);

            progressTracker.sendProgress(scanId, channelId, 50, "📊 Generating Broken & Duplicate Links Report...");
            Thread.sleep(3000);

            String brokenAndDuplicateLinksReport = brokenLinkAndDuplicateTracker.generateReport(url, scanId);

            progressTracker.sendProgress(scanId, channelId, 100, "✅ Broken & Duplicate Links Scan Completed!");
            progressTracker.sendReport(scanId, channelId, "❌ **Broken & Duplicate Links Report**", brokenAndDuplicateLinksReport);

        } catch (IOException | IllegalArgumentException | InterruptedException e) {
            progressTracker.sendProgress(scanId, channelId, 100, "❌ Scan Failed: " + e.getMessage());
        }
        return url;
    }

    Document scrape(String url) throws IOException {
        return Jsoup.connect(url).timeout(TIMEOUT).get();
    }


    private String generateMetaTagReport(String url, Map<String, List<String>> metaTags) {
        StringBuilder report = new StringBuilder("🏷️ **SEO Meta Tag Report for:** " + url + "\n\n");
        metaTags.forEach((category, tags) -> {
            report.append(" ").append(category).append("\n");
            tags.forEach(tag -> report.append("- ").append(tag).append("\n"));
            report.append("\n");
        });
        return report.toString();
    }



    private String generateCategorizedLinkReport(String url, CategorizedLink categorizedLink) {
        StringBuilder report = new StringBuilder("🔗 **Categorized Link Report for:** " + url + "\n\n");

        report.append("\n- **Navigation Links:**\n");
        if (categorizedLink.navigationLinks().isEmpty()) {
            report.append("❌ No navigation links found.\n");
        } else {
            categorizedLink.navigationLinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        report.append("\n- **Footer Links:**\n");
        if (categorizedLink.footerLinks().isEmpty()) {
            report.append("❌ No footer links found.\n");
        } else {
            categorizedLink.footerLinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        report.append("\n- **Sidebar Links:**\n");
        if (categorizedLink.sidebarLinks().isEmpty()) {
            report.append("❌ No sidebar links found.\n");
        } else {
            categorizedLink.sidebarLinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        report.append("\n- **Breadcrumb Links:**\n");
        if (categorizedLink.breadcrumbLinks().isEmpty()) {
            report.append("❌ No breadcrumb links found.\n");
        } else {
            categorizedLink.breadcrumbLinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        report.append("\n- **Outbound Links:**\n");
        if (categorizedLink.outboundLinks().isEmpty()) {
            report.append("❌ No outbound links found.\n");
        } else {
            categorizedLink.outboundLinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        report.append("\n- **Backlinks:**\n");
        if (categorizedLink.backlinks().isEmpty()) {
            report.append("❌ No backlinks found.\n");
        } else {
            categorizedLink.backlinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        report.append("\n- **Affiliate Links:**\n");
        if (categorizedLink.affiliateLinks().isEmpty()) {
            report.append("❌ No affiliate links found.\n");
        } else {
            categorizedLink.affiliateLinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        report.append("\n- **Social Media Links:**\n");
        if (categorizedLink.socialMediaLinks().isEmpty()) {
            report.append("❌ No social media links found.\n");
        } else {
            categorizedLink.socialMediaLinks().forEach(link -> report.append("- ").append(link).append("\n"));
        }

        return report.toString();
    }



    private class MetaTagExtractor {
        private final Document document;

        public MetaTagExtractor(Document document) {
            this.document = document;
        }

        public Map<String, List<String>> extractMetaTags() {
            Map<String, List<String>> metaTags = new LinkedHashMap<>();

            // Core SEO Tags 🔍
            metaTags.put("🔍 *Core SEO Tags*", List.of(
                    formatTag("📖 **Title Tag**", document.selectFirst("title"), "<title>Your Page Title</title>"),
                    formatTag("📝 **Meta Description**", document.selectFirst("meta[name=description]"), "<meta name='description' content='Brief summary of the page'>"),
                    formatTag("🔑 **Meta Keywords**", document.selectFirst("meta[name=keywords]"), "<meta name='keywords' content='keyword1, keyword2'>")
            ));

            // Crawling & Indexing Tags 🕷️
            metaTags.put("🕷️ *Crawling & Indexing Tags*", List.of(
                    formatTag("🤖 **Meta Robots**", document.selectFirst("meta[name=robots]"), "<meta name='robots' content='index, follow'>"),
                    formatTag("🔗 **Canonical Tag**", document.selectFirst("link[rel=canonical]"), "<link rel='canonical' href='https://example.com/page'>")
            ));

            // Social Media Tags 📢
            metaTags.put("📢 *Social Media Tags*", List.of(
                    formatTag("📌 **Open Graph Title**", document.selectFirst("meta[property=og:title]"), "<meta property='og:title' content='Your Open Graph Title'>"),
                    formatTag("📌 **Open Graph Description**", document.selectFirst("meta[property=og:description]"), "<meta property='og:description' content='Your Open Graph Description'>"),
                    formatTag("🖼️ **Open Graph Image**", document.selectFirst("meta[property=og:image]"), "<meta property='og:image' content='https://example.com/image.jpg'>"),
                    formatTag("🐦 **Twitter Card**", document.selectFirst("meta[name=twitter:card]"), "<meta name='twitter:card' content='summary_large_image'>")
            ));

            // Author & Mobile Optimization 📱
            metaTags.put("📱 *Author & Mobile Optimization*", List.of(
                    formatTag("✍️ **Meta Author**", document.selectFirst("meta[name=author]"), "<meta name='author' content='Your Name'>"),
                    formatTag("📱 **Meta Viewport**", document.selectFirst("meta[name=viewport]"), "<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            ));

            return metaTags;
        }

        private String formatTag(String tagName, Element element, String exampleStructure) {
            if (element != null) {
                String content = element.tagName().equalsIgnoreCase("title") ? element.text() : element.attr("content");
                return tagName + " -> " + (content.isEmpty() ? "(No content found)" : content);
            } else {
                return tagName + " -> (Missing)\n  # Quick Fix --> " + exampleStructure + "\n";
            }
        }
    }
}


