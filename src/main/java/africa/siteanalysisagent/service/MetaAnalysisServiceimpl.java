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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
    public SiteAnalysis analyzeSite(String channelId, String baseUrl) throws IOException {
        try {
            // Validate base URL first
            if (!webScrapeService.isValidUrlForScraping(baseUrl)) {
                throw new IllegalArgumentException("Invalid base URL: " + baseUrl);
            }

            // 1. Crawl all pages and their links
            Map<String, List<String>> siteMap = crawlSite(baseUrl);

            // 2. Validate all links across all pages
            Map<String, Map<String, String>> linkStatusMap = validateSiteLinks(baseUrl, siteMap);

            // 3. Categorize and analyze links
            Map<String, CategorizedLink> categorizedPages = new ConcurrentHashMap<>();
            List<Future<?>> categorizationFutures = new ArrayList<>();

            siteMap.forEach((pageUrl, links) -> {
                categorizationFutures.add(executor.submit(() -> {
                    try {
                        Document doc = Jsoup.parse(webScrapeService.scrapeWithRetry(pageUrl));
                        categorizedPages.put(pageUrl, linkService.categorizeLinks(doc));
                    } catch (Exception e) {
                        log.error("Failed to categorize links for {}", pageUrl, e);
                    }
                }));
            });

            // Wait for categorization to complete
            for (Future<?> future : categorizationFutures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Analysis interrupted", e);
                } catch (ExecutionException e) {
                    log.error("Error during categorization", e);
                }
            }

            // 4. Build comprehensive analysis
            SiteAnalysis analysis = buildSiteAnalysis(baseUrl, siteMap, linkStatusMap, categorizedPages);
            siteAnalysisCache.put(baseUrl, analysis);
            sendSiteAnalysisToTelex(channelId, baseUrl, analysis);

            return analysis;
        } catch (Exception e) {
            log.error("Site analysis failed for {}", baseUrl, e);
            throw new IOException("Error during analysis");
        }
    }


    private void sendSiteAnalysisToTelex(String channelId,String baseUrl, SiteAnalysis analysis) {
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

        telexService.sendMessage(channelId, message);


        if (!analysis.getBrokenLinks().isEmpty()) {
            sendBrokenLinksList(channelId, analysis.getBrokenLinks());
        }

        // 4. Send action prompt
        List<Button> buttons = Arrays.asList(
                new Button("📅 Schedule Scan", "schedule", "schedule_scan:" + baseUrl),
                new Button("📊 SEO Report", "report", "seo_report:" + baseUrl),
                new Button("🔧 Fix Issues", "fix", "fix_issues:" + baseUrl)
        );
        telexService.sendMessage(channelId,message, buttons);


    }
private void sendBrokenLinksList(String channelId, List<String> brokenLinks) {
        try {
            // Get unique broken links first
            List<String> uniqueBrokenLinks = brokenLinks.stream()
                    .distinct()
                    .collect(Collectors.toList());

            int chunkSize = 15;
            for (int i = 0; i < uniqueBrokenLinks.size(); i += chunkSize) {
                List<String> chunk = uniqueBrokenLinks.subList(i, Math.min(i + chunkSize, uniqueBrokenLinks.size()));

                StringBuilder message = new StringBuilder();
                if (i == 0) {
                    message.append("❌ *Broken Links Found (").append(uniqueBrokenLinks.size()).append(")*\n");
                } else {
                    message.append("(Continued) Links ").append(i+1).append("-")
                            .append(Math.min(i+chunkSize, uniqueBrokenLinks.size())).append(":\n");
                }

                chunk.forEach(link -> message.append("• ").append(link).append("\n"));

                if (i + chunkSize < uniqueBrokenLinks.size()) {
                    message.append("\nMore broken links coming...");
                }

                telexService.sendMessage(channelId, message.toString());
            }
        } catch (Exception e) {
            log.error("Failed to send broken links to Telex", e);
        }
    }

    private SiteAnalysis buildSiteAnalysis(String baseUrl,
                                           Map<String, List<String>> siteMap,
                                           Map<String, Map<String, String>> linkStatusMap,
                                           Map<String, CategorizedLink> categorizedPages) {
        // Calculate statistics using Sets to avoid duplicates
        Set<String> allActiveLinks = new HashSet<>();
        Set<String> allBrokenLinks = new HashSet<>();

        // Process link status map
        linkStatusMap.values().forEach(pageLinks ->
                pageLinks.forEach((link, status) -> {
                    if (status.startsWith("ACTIVE")) {
                        allActiveLinks.add(link);
                    } else if (status.startsWith("BROKEN") || status.startsWith("ERROR")) {
                        allBrokenLinks.add(link);
                    }
                })
        );

        // Get unique links by category
        Set<String> allInternalLinks = categorizedPages.values().stream()
                .flatMap(cl -> cl.getInternalLinks(baseUrl).stream())
                .collect(Collectors.toSet());

        Set<String> allExternalLinks = categorizedPages.values().stream()
                .flatMap(cl -> cl.getExternalLinks(baseUrl).stream())
                .collect(Collectors.toSet());

        Set<String> allResourceLinks = categorizedPages.values().stream()
                .flatMap(cl -> cl.getResourceLinks().stream())
                .collect(Collectors.toSet());

        return SiteAnalysis.builder()
                .baseUrl(baseUrl)
                .scannedPages(new ArrayList<>(siteMap.keySet()))
                .totalPagesScanned(siteMap.size())
                .totalLinksFound(allInternalLinks.size() + allExternalLinks.size() + allResourceLinks.size())
                .pageLinksMap(siteMap)
                .linkStatusMap(flattenStatusMap(linkStatusMap))
                .totalInternalLinks(allInternalLinks.size())
                .totalExternalLinks(allExternalLinks.size())
                .totalResourceLinks(allResourceLinks.size())
                .totalActiveLinks(allActiveLinks.size())
                .totalBrokenLinks(allBrokenLinks.size())
                .categorizedLinks(categorizedPages)
                .internalLinks(new ArrayList<>(allInternalLinks))
                .externalLinks(new ArrayList<>(allExternalLinks))
                .resourceLinks(new ArrayList<>(allResourceLinks))
                .activeLinks(new ArrayList<>(allActiveLinks))
                .brokenLinks(new ArrayList<>(allBrokenLinks))
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

    private Map<String, List<String>> crawlSite(String baseUrl) throws IOException, InterruptedException {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL cannot be null or blank");
        }

        // Normalize the base URL first
        baseUrl = normalizeLink(baseUrl);
        if (!webScrapeService.isValidUrlForScraping(baseUrl)) {
            throw new IllegalArgumentException("Invalid base URL: " + baseUrl);
        }

        Map<String, List<String>> siteMap = new LinkedHashMap<>();
        Set<String> visitedUrls = new HashSet<>();
        Set<String> allSeenLinks = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(baseUrl);

        while (!queue.isEmpty() && visitedUrls.size() < 50) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Crawling interrupted");
            }

            String currentUrl = queue.poll();
            if (currentUrl == null) {
                continue;
            }

            if (visitedUrls.add(currentUrl)) {
                try {
                    // Get document with retry logic
                    String htmlContent = webScrapeService.scrapeWithRetry(currentUrl, 3);
                    Document doc = Jsoup.parse(htmlContent);

                    List<String> rawLinks = extractAllLinks(doc, baseUrl);

                    // Process links with better filtering
                    List<String> uniqueLinks = rawLinks.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(link -> !link.isEmpty())
                            .map(this::normalizeLink)
                            .filter(link -> {
                                // Skip invalid URLs and already seen links
                                if (!webScrapeService.isValidUrlForScraping(link)) {
                                    return false;
                                }
                                return allSeenLinks.add(link);
                            })
                            .collect(Collectors.toList());

                    siteMap.put(currentUrl, uniqueLinks);

                    // Add internal links to queue with better filtering
                    String finalBaseUrl = baseUrl;
                    uniqueLinks.stream()
                            .filter(link -> link.startsWith(finalBaseUrl))
                            .filter(link -> !visitedUrls.contains(link))
                            .forEach(queue::offer);

                } catch (IOException e) {
                    log.error("Failed to crawl page after retries: {}", currentUrl, e);
                    siteMap.put(currentUrl, List.of("CRAWL_ERROR: " + e.getMessage()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Crawling interrupted while processing: {}", currentUrl, e);
                    siteMap.put(currentUrl, List.of("CRAWL_INTERRUPTED"));
                    throw new IOException("Crawling interrupted", e);
                }
            }
        }
        return siteMap;
    }

    private String normalizeLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }

        try {
            // Handle relative URLs
            if (!link.startsWith("http")) {
                return link;
            }

            URI uri = new URI(link);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String normalizedPath = path == null ? "" : path.replaceAll("/+", "/");

            // Rebuild URI without fragment, query params, and with normalized path
            return new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    normalizedPath,
                    null,  // No query
                    null   // No fragment
            ).toString().replaceAll("/$", "");
        } catch (URISyntaxException e) {
            log.warn("Invalid URL syntax: {}", link, e);
            return null;
        }
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

    }