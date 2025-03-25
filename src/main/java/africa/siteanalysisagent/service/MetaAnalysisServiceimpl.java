package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.CategorizedLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAnalysisServiceimpl implements MetaAnalysisService {

    private final TelexService telexService;
    private final ProgressTracker progressTracker;
    private final LinkCrawlAndCategorizationService linkCrawlAndCategory;
    private final BrokenLinkAndDuplicateTracker brokenLinkAndDuplicateTracker;
    private final GeminiService geminiService;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, String> pendingOptimizations = new HashMap<>();


    private static final int TIMEOUT = 10000; // 10 seconds

    private final Map<String, Boolean> activeScans = new HashMap<>(); // Tracks active scans per channel
    private final Map<String, Integer> invalidInputCounts = new HashMap<>(); // Tracks invalid inputs per channel

    private void sendOrderedProgress(String scanId, String channelId, int progress, String message) {
        try {
            Thread.sleep(2000); // Delay to maintain message order
            progressTracker.sendProgress(scanId, channelId, progress, message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isSingleUrl(String url) {
        return url != null && url.matches(".*/.*");
    }

    @Override
    public void generateSeoReport(String url, String scanId, String channelId, Consumer<String> callback) {

        log.info("Starting SEO analysis for URL: {}", url);

        if (activeScans.getOrDefault(channelId, false)) {
            telexService.sendMessage(channelId, "⚠️ A scan is already in progress for this channel. Please wait.");
            return;
        }

        try {
            activeScans.put(channelId, true);

            sendOrderedProgress(scanId, channelId, 10, "🔄 Starting SEO Meta Tag Scan...");
            Document document = scrape(url);
            sendOrderedProgress(scanId, channelId, 40, "🏷️ Extracting Meta Tags...");
            MetaTagExtractor metaTagExtractor = new MetaTagExtractor(document);
            Map<String, List<String>> metaTags = metaTagExtractor.extractMetaTags();
            sendOrderedProgress(scanId, channelId, 80, "📊 Generating SEO Meta Tag Report...");
            String seoMetaTagReport = generateMetaTagReport(url, metaTags);
            sendOrderedProgress(scanId, channelId, 100, "✅ SEO Meta Tag Scan Completed!");

            // Send the SEO Meta Tag Report to Telex
            sendReportAfterTelex(scanId, channelId, "🏷️ **SEO Meta Tag Report**", seoMetaTagReport);

            sendOrderedProgress(scanId, channelId, 10, "🔄 Starting Categorized Link Scan...");
            sendOrderedProgress(scanId, channelId, 40, "🔗 Scanning Links...");
            CategorizedLink categorizedLinks = linkCrawlAndCategory.categorizedLinkDto(document);
            sendOrderedProgress(scanId, channelId, 80, "📊 Generating Categorized Link Report...");
            String categorizedLinkReport = generateCategorizedLinkReport(url, categorizedLinks);
            sendOrderedProgress(scanId, channelId, 100, "✅ Categorized Link Scan Completed!");

            // Send the Categorized Link Report to Telex
            sendReportAfterTelex(scanId, channelId, "🔗 **Categorized Link Report**", categorizedLinkReport);

            sendOrderedProgress(scanId, channelId, 10, "🔄 Starting Broken & Duplicate Links Scan...");
            linkCrawlAndCategory.detectBrokenAndDuplicateLinks(scanId, channelId, categorizedLinks);
            sendOrderedProgress(scanId, channelId, 50, "📊 Generating Broken & Duplicate Links Report...");
            String brokenAndDuplicateLinksReport = brokenLinkAndDuplicateTracker.generateReport(url, scanId);
            sendOrderedProgress(scanId, channelId, 100, "✅ Broken & Duplicate Links Scan Completed!");

            // Send the Broken & Duplicate Links Report to Telex
            sendReportAfterTelex(scanId, channelId, "❌ **Broken & Duplicate Links Report**", brokenAndDuplicateLinksReport);

            sendOrderedProgress(scanId, channelId, 10, "📊 Calculating SEO Score...");
            Map<String, Object> analysisResult = geminiService.analyzeSeo(url, metaTags, categorizedLinks);
            int seoScore = (int) analysisResult.getOrDefault("seo_score", 0);
            String recommendations = (String) analysisResult.getOrDefault("optimization_suggestions", "No recommendations found.");
            String optimizedMetags = (String) analysisResult.getOrDefault("optimized_meta_tags", "No optimized meta tags found.");

            sendOrderedProgress(scanId, channelId, 100, "✅ SEO Analysis Completed!");

            String fullReport = "📊 **Final SEO Score:** " + seoScore + "/100\n\n💡 **AI Recommendations:**\n" + recommendations + "\n\n";

            sendReportAfterTelex(scanId, channelId, "📊 **Final SEO Score Report**", fullReport);
            // Execute the callback with the final report
            callback.accept(fullReport);


            pendingOptimizations.put(channelId, optimizedMetags);
            log.info("Stored optimized meta tags for channel {}: {}", channelId, optimizedMetags);


//             Send the Final SEO Score Report to Telex

            telexService.sendInteractiveMessage(channelId,
                    "📊 **SEO Analysis Complete!**\nWould you like to apply the AI-optimized fixes?\n👉 Type `apply_fixes` to apply or `ignore` to skip.",
                    List.of(new Button("✅ Apply Fixes", "apply_fixes"), new Button("❌ Ignore", "ignore")));


        } catch (IOException e) {
            log.error("❌ Error during SEO analysis: {}", e.getMessage(), e);
            sendOrderedProgress(scanId, channelId, 100, "❌ Scan Failed: " + e.getMessage());
            telexService.sendMessage(channelId, "❌ Error generating SEO report: " + e.getMessage());

        } finally {
            activeScans.put(channelId, false);
        }


    }

    @Override
    public String getOptimizedMetags(String channelId) {
        log.info("Retrieving optimized meta tags for channel {}: {}", channelId, pendingOptimizations.get(channelId));
        return pendingOptimizations.getOrDefault(channelId, "⚠️ No optimized meta tags found! Please run a scan first.");
    }

    @Override
    public void clearOptimizedMetags(String channelId) {
        log.info("Clearing optimized meta tags for channel {}", channelId);
        pendingOptimizations.remove(channelId);
    }



    private void sendReportAfterTelex(String scanId, String channelId, String title, String reportContent) {
        if (channelId == null || channelId.isEmpty()) {
            log.error("❌ Cannot send Telex report: channel_id is missing.");
            return;
        }

        try {
            // Block until the report is sent
            ResponseEntity<String> response = telexService.sendMessage(channelId, reportContent).join();

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Report sent successfully to Telex: {}", response.getBody());
            } else {
                log.error("❌ Failed to send report to Telex: {}", response.getBody());
            }
        } catch (Exception ex) {
            log.error("❌ Error sending report to Telex: {}", ex.getMessage());
        }
    }

    public void handleUserInput(String channelId, String userInput, String url) {
        if (userInput.equalsIgnoreCase("apply_fixes")) {
            applyOptimizedMetaTags(channelId, url);
            invalidInputCounts.remove(channelId); // Reset invalid input count
        } else if (userInput.equalsIgnoreCase("ignore")) {
            telexService.sendMessage(channelId, "Thank you for using our service! Let us know if you need further assistance.");
            invalidInputCounts.remove(channelId); // Reset invalid input count
        } else {
            int count = invalidInputCounts.getOrDefault(channelId, 0) + 1;
            invalidInputCounts.put(channelId, count);

            if (count >= 3) {
                telexService.sendMessage(channelId, "❌ Too many invalid inputs. Operation canceled.");
                invalidInputCounts.remove(channelId);
            } else {
                telexService.sendMessage(channelId, "❌ Invalid input. Please type `apply_fixes` or `ignore`.");
            }
        }
    }

    private void applyOptimizedMetaTags(String channelId, String url) {
        try {
            // Scrape the website
            Document document = scrape(url);

            // Extract meta tags
            MetaTagExtractor metaTagExtractor = new MetaTagExtractor(document);
            Map<String, List<String>> metaTags = metaTagExtractor.extractMetaTags();

            // Extract categorized links
            CategorizedLink categorizedLinks = linkCrawlAndCategory.categorizedLinkDto(document);

            // Generate AI-optimized meta tags
            Map<String, String> optimizedMetaTagsResult = geminiService.generateOptimizedMetaTags(url, metaTags, categorizedLinks);
            String optimizedMetaTags = optimizedMetaTagsResult.getOrDefault("optimized_meta_tags", "No optimized meta tags found.");

            // Send optimized meta tags to Telex
            telexService.sendMessage(channelId, "🤖 **Optimized Meta Tags:**\n" + optimizedMetaTags);

        } catch (IOException e) {
            telexService.sendMessage(channelId, "❌ Failed to apply fixes: " + e.getMessage());
        }
    }

    private Document scrape(String url) throws IOException {
        int maxRetries = 3; // Retry up to 3 times
        int retryCount = 0;
        IOException lastException = null;

        while (retryCount < maxRetries) {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Referer", "https://www.google.com/")
                        .timeout(30000) // 30 seconds timeout
                        .get();
            } catch (IOException e) {
                lastException = e;
                retryCount++;
                log.warn("⚠️ Attempt {} failed for URL: {}. Retrying...", retryCount, url);
                try {
                    Thread.sleep(5000); // Wait 5 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", ie);
                }
            }
        }

        throw new IOException("Failed to scrape URL after " + maxRetries + " attempts: " + url, lastException);
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