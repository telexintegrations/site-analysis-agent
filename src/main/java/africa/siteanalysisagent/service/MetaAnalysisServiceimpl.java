package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.CategorizedLink;
import africa.siteanalysisagent.dto.SiteAnalysis;
import africa.siteanalysisagent.model.SEOReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaAnalysisServiceimpl implements MetaAnalysisService {

    private final TelexService telexService;
    private final LinkCrawlAndCategorizationService linkService;
    private final BrokenLinkAndDuplicateTracker brokenLinkTracker;
    private final GeminiService geminiService;
    private final WebScrapeService webScrapeService;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private final Map<String, SiteAnalysis> siteAnalysisCache = new ConcurrentHashMap<>();
    private final Map<String, SEOReport> seoReportCache = new ConcurrentHashMap<>();

    @Override
    public SiteAnalysis analyzeSite(String baseUrl) throws IOException {
        // 1. Crawl all pages and their links
        Map<String, List<String>> siteMap = crawlSite(baseUrl);

        // 2. Validate all links across all pages
        Map<String, Map<String, String>> linkStatusMap = validateSiteLinks(baseUrl, siteMap);

        // 3. Categorize and analyze links
        Map<String, CategorizedLink> categorizedPages = new HashMap<>();
        siteMap.forEach((pageUrl, links) -> {
            try {
                Document doc = webScrapeService.scrapeWithRetry(pageUrl, 3);
                categorizedPages.put(pageUrl, linkService.categorizeLinks(doc));
            } catch (IOException | InterruptedException e) {
                log.error("Failed to categorize links for {}", pageUrl, e);
            }
        });

        // 4. Build comprehensive analysis
        SiteAnalysis analysis = buildSiteAnalysis(baseUrl, siteMap, linkStatusMap, categorizedPages);
        siteAnalysisCache.put(baseUrl, analysis);
        sendSiteAnalysisToTelex(baseUrl, analysis);

        return analysis;
    }

    @Override
    public SEOReport generateFullReport(String url) throws IOException {
        try {
            Document document = webScrapeService.scrapeWithRetry(url, 3);

            // 1. Extract meta tags
            MetaTagExtractor metaTagExtractor = new MetaTagExtractor(document);
            Map<String, List<String>> metaTags = metaTagExtractor.extractMetaTags();

            // 2. Categorize links
            CategorizedLink categorizedLinks = linkService.categorizeLinks(document);

            // 3. Check for broken links
            brokenLinkTracker.analyzeLinks(url, categorizedLinks);

            // 4. Get AI analysis
            Map<String, Object> aiAnalysis = (Map<String, Object>) geminiService.analyzeSEO(url, metaTags, categorizedLinks);

            // 5. Build and cache report
            SEOReport report = buildSEOReport(url, document, metaTags, categorizedLinks, aiAnalysis);
            seoReportCache.put(url, report);

            sendSeoReportToTelex(url, report);
            return report;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Scraping was interrupted", e);
        }
    }


    private void sendSiteAnalysisToTelex(String baseUrl, SiteAnalysis analysis) {
        String message = String.format("""
            🏷️ *Site Analysis Completed*
            ━━━━━━━━━━━━━━━
            • *URL:* %s
            • *Pages Scanned:* %d
            • *Total Links:* %d
            • *Internal Links:* %d
            • *External Links:* %d
            • *Broken Links:* %d
            ━━━━━━━━━━━━━━━
            Use command 'analyze %s' for details
            """,
                baseUrl,
                analysis.getTotalPagesScanned(),
                analysis.getTotalLinksFound(),
                analysis.getTotalInternalLinks(),
                analysis.getTotalExternalLinks(),
                analysis.getTotalBrokenLinks(),
                baseUrl
        );

        telexService.sendMessage("seo_reports", message)
                .exceptionally(e -> {
                    log.error("Failed to send analysis to Telex", e);
                    return null;
                });
    }

    private void sendSeoReportToTelex(String url, SEOReport report) {
        String message = String.format("""
            📊 *SEO Report Generated*
            ━━━━━━━━━━━━━━━
            • *URL:* %s
            • *Score:* %d/100
            • *Top Issue:* %s
            ━━━━━━━━━━━━━━━
            Use command 'report %s' for details
            """,
                url,
                report.getScore(),
                getTopRecommendation(report.getRecommendations()),
                url
        );

        telexService.sendMessage("seo_reports", message);
    }

    private String getTopRecommendation(String recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "No critical issues found";
        }
        return recommendations.split("\n")[0];
    }

    private SiteAnalysis buildSiteAnalysis(String baseUrl,
                                           Map<String, List<String>> siteMap,
                                           Map<String, Map<String, String>> linkStatusMap,
                                           Map<String, CategorizedLink> categorizedPages) {
        // Calculate statistics from categorized links
        int totalInternalLinks = categorizedPages.values().stream()
                .mapToInt(cl -> cl.getInternalLinkCount(baseUrl))
                .sum();

        int totalExternalLinks = categorizedPages.values().stream()
                .mapToInt(cl -> cl.getExternalLinkCount(baseUrl))
                .sum();

        int totalResourceLinks = categorizedPages.values().stream()
                .mapToInt(CategorizedLink::getResourceLinkCount)
                .sum();

        // Extract all active/broken links
        List<String> allActiveLinks = linkStatusMap.values().stream()
                .flatMap(m -> m.entrySet().stream())
                .filter(e -> e.getValue().startsWith("ACTIVE"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> allBrokenLinks = linkStatusMap.values().stream()
                .flatMap(m -> m.entrySet().stream())
                .filter(e -> e.getValue().startsWith("BROKEN") || e.getValue().startsWith("ERROR"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Get all unique links by category
        List<String> allInternalLinks = categorizedPages.values().stream()
                .flatMap(cl -> cl.getInternalLinks(baseUrl).stream())
                .distinct()
                .collect(Collectors.toList());

        List<String> allExternalLinks = categorizedPages.values().stream()
                .flatMap(cl -> cl.getExternalLinks(baseUrl).stream())
                .distinct()
                .collect(Collectors.toList());

        List<String> allResourceLinks = categorizedPages.values().stream()
                .flatMap(cl -> cl.getResourceLinks().stream())  // Changed to lambda expression
                .distinct()
                .collect(Collectors.toList());

        return SiteAnalysis.builder()
                .baseUrl(baseUrl)
                .scannedPages(new ArrayList<>(siteMap.keySet()))
                .totalPagesScanned(siteMap.size())
                .totalLinksFound(totalInternalLinks + totalExternalLinks + totalResourceLinks)
                .pageLinksMap(siteMap)
                .linkStatusMap(flattenStatusMap(linkStatusMap))
                .totalInternalLinks(totalInternalLinks)
                .totalExternalLinks(totalExternalLinks)
                .totalResourceLinks(totalResourceLinks)
                .totalActiveLinks(allActiveLinks.size())
                .totalBrokenLinks(allBrokenLinks.size())
                .categorizedLinks(categorizedPages)
                .internalLinks(allInternalLinks)
                .externalLinks(allExternalLinks)
                .resourceLinks(allResourceLinks)
                .activeLinks(allActiveLinks)
                .brokenLinks(allBrokenLinks)
                .detailedLinkStatus(linkStatusMap)
                .build();
    }
    private SEOReport buildSEOReport(String url,
                                     Document document,
                                     Map<String, List<String>> metaTags,
                                     CategorizedLink categorizedLinks,
                                     Map<String, Object> aiAnalysis) {
        return SEOReport.builder()
                .url(url)
                .metaTags(metaTags)
                .categorizedLinks(categorizedLinks)
                .score((int) aiAnalysis.getOrDefault("seo_score", 0))
                .recommendations((String) aiAnalysis.getOrDefault("optimization_suggestions", ""))
                .optimizedMetaTags((String) aiAnalysis.getOrDefault("optimized_meta_tags", ""))
                .rawHtml(document.html())
                .build();
    }

    private Map<String, List<String>> crawlSite(String baseUrl) {
        Map<String, List<String>> siteMap = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(baseUrl);

        while (!queue.isEmpty() && visited.size() < 50) {
            String currentUrl = queue.poll();
            if (visited.add(currentUrl)) {
                try {
                    Document doc = webScrapeService.scrapeWithRetry(currentUrl, 3);
                    List<String> links = extractAllLinks(doc, baseUrl);
                    siteMap.put(currentUrl, links);

                    // Add internal links to queue
                    links.stream()
                            .filter(link -> link.startsWith(baseUrl))
                            .forEach(queue::offer);

                } catch (IOException | InterruptedException e) {
                    log.error("Failed to crawl page: {}", currentUrl, e);
                    siteMap.put(currentUrl, List.of("CRAWL_ERROR: " + e.getMessage()));
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return siteMap;
    }

    private Map<String, Map<String, String>> validateSiteLinks(String baseUrl, Map<String, List<String>> siteMap) {
        Map<String, Map<String, String>> result = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();

        siteMap.forEach((pageUrl, links) -> {
            Map<String, String> pageResults = new ConcurrentHashMap<>();
            result.put(pageUrl, pageResults);

            links.forEach(link -> {
                futures.add(executor.submit(() -> {
                    pageResults.put(link, checkLinkStatus(baseUrl, link));
                }));
            });
        });

        // Wait for completion
        futures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Link validation interrupted", e);
            } catch (ExecutionException e) {
                log.error("Link validation error", e);
            }
        });

        return result;
    }

    private String checkLinkStatus(String baseUrl, String link) {
        try {
            String absoluteUrl = link.startsWith("http") ? link : baseUrl + link;
            Connection.Response response = Jsoup.connect(absoluteUrl)
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .execute();

            return response.statusCode() < 400 ?
                    "ACTIVE (" + response.statusCode() + ")" :
                    "BROKEN (" + response.statusCode() + ")";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private List<String> extractAllLinks(Document doc, String baseUrl) {
        return doc.select("a[href]").stream()
                .map(link -> link.absUrl("href"))
                .filter(href -> !href.isBlank())
                .map(href -> href.startsWith("http") ? href : baseUrl + href)
                .collect(Collectors.toList());
    }

    private Map<String, String> flattenStatusMap(Map<String, Map<String, String>> nestedMap) {
        return nestedMap.values().stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing));
    }

    private static class MetaTagExtractor {
        private final Document document;

        public MetaTagExtractor(Document document) {
            this.document = document;
        }

        public Map<String, List<String>> extractMetaTags() {
            Map<String, List<String>> metaTags = new LinkedHashMap<>();

            // Core SEO Tags
            metaTags.put("Core SEO Tags", List.of(
                    formatTag("Title Tag", document.selectFirst("title")),
                    formatTag("Meta Description", document.selectFirst("meta[name=description]")),
                    formatTag("Meta Keywords", document.selectFirst("meta[name=keywords]"))
            ));

            return metaTags;
        }

        private String formatTag(String tagName, Element element) {
            if (element != null) {
                String content = element.tagName().equals("title") ? element.text() : element.attr("content");
                return tagName + ": " + (content.isEmpty() ? "Not found" : content);
            }
            return tagName + ": Missing";
        }
    }
}