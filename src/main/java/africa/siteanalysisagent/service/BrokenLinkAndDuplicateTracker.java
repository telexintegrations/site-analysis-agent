package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import africa.siteanalysisagent.dto.SiteAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BrokenLinkAndDuplicateTracker {

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_THREADS = 10;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

    public int getBrokenLinksCount(String baseUrl, CategorizedLink links) throws ExecutionException, InterruptedException {
        return getBrokenLinksDetails(baseUrl, links).size();
    }

    public List<String> getBrokenLinksDetails(SiteAnalysis analysis) {
        if (analysis == null || analysis.getCategorizedLinks() == null) {
            return Collections.emptyList();
        }
        return getBrokenLinksDetails(analysis.getBaseUrl(), (CategorizedLink) analysis.getCategorizedLinks());
    }

    public List<String> getBrokenLinksDetails(String baseUrl, CategorizedLink links) {
        if (links == null) {
            return Collections.emptyList();
        }

        try {
            Map<String, List<String>> analysisResults = analyzeLinks(baseUrl, links);
            return analysisResults.getOrDefault("broken_links", Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to analyze links: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    Map<String, List<String>> analyzeLinks(String baseUrl, CategorizedLink links) {
        Map<String, List<String>> result = new HashMap<>();
        List<Future<LinkCheck>> futures = new ArrayList<>();

        // Get all unique links first
        Set<String> uniqueLinks = links.getAllLinks(); // This now returns a Set

        // Check each unique link
        for (String url : uniqueLinks) {
            futures.add(executor.submit(() -> checkLink(baseUrl, url)));
        }

        // Process results
        List<String> brokenLinks = new ArrayList<>();
        List<String> duplicates = findDuplicates(links);

        for (Future<LinkCheck> future : futures) {
            try {
                LinkCheck check = future.get();
                if (!check.isValid()) {
                    brokenLinks.add(formatBrokenLink(check));
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Link check failed: {}", e.getMessage());
                brokenLinks.add("Error checking link: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        result.put("broken_links", brokenLinks);
        result.put("duplicate_links", duplicates);
        return result;
    }

    private List<String> findDuplicates(CategorizedLink links) {
        // Get all links from all categories
        List<String> allLinks = new ArrayList<>();
        allLinks.addAll(links.getNavigationLinks());
        allLinks.addAll(links.getFooterLinks());
        allLinks.addAll(links.getSidebarLinks());
        allLinks.addAll(links.getBreadcrumbLinks());
        allLinks.addAll(links.getOutboundLinks());
        allLinks.addAll(links.getBacklinks());
        allLinks.addAll(links.getAffiliateLinks());
        allLinks.addAll(links.getSocialMediaLinks());
        allLinks.addAll(links.getImageLinks());
        allLinks.addAll(links.getScriptLinks());
        allLinks.addAll(links.getStylesheetLinks());

        // Find duplicates
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String url : allLinks) {
            frequencyMap.put(url, frequencyMap.getOrDefault(url, 0) + 1);
        }

        return frequencyMap.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> formatDuplicate(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private String formatBrokenLink(LinkCheck check) {
        return String.format("%s (Status: %d %s)",
                check.url(), check.status(), check.message());
    }

    private String formatDuplicate(String url, int count) {
        return String.format("%s appears %d times", url, count);
    }

    private LinkCheck checkLink(String baseUrl, String url) {
        try {
            String absoluteUrl = CategorizedLink.normalizeUrl(baseUrl, url);
            Connection.Response response = Jsoup.connect(absoluteUrl)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            return new LinkCheck(url, response.statusCode(), response.statusMessage());
        } catch (IOException e) {
            return new LinkCheck(url, 500, "Connection failed: " + e.getMessage());
        }
    }

    private record LinkCheck(String url, int status, String message) {
        boolean isValid() {
            return status >= 200 && status < 400; // Consider all 2xx and 3xx as valid
        }
    }
}