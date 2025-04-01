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

@Service
@Slf4j
@RequiredArgsConstructor
public class BrokenLinkAndDuplicateTracker {

    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_THREADS = 10;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

    // New method to get just broken links count
    public int getBrokenLinksCount(String baseUrl, CategorizedLink links) throws ExecutionException, InterruptedException {
        return analyzeLinks(baseUrl, links).get("broken_links").size();
    }

    // New method to get broken links with details
    public List<String> getBrokenLinksDetails(SiteAnalysis analysis) {
        if (analysis == null) return List.of();
        return analyzeLinks(analysis.getBaseUrl(), (CategorizedLink) analysis.getCategorizedLinks())
                .get("broken_links");
    }

    // Existing method (modified to be private since we'll use the new public methods)
    Map<String, List<String>> analyzeLinks(String baseUrl, CategorizedLink links) {
        Map<String, List<String>> result = new HashMap<>();
        List<Future<LinkCheck>> futures = new ArrayList<>();

        // Check all unique links
        Set<String> uniqueLinks = new HashSet<>(links.getAllLinks());
        List<String> allLinks = new ArrayList<>(uniqueLinks);

        for (String url : allLinks) {
            futures.add(executor.submit(() -> checkLink(baseUrl, url)));
        }

        // Process results
        List<String> brokenLinks = new ArrayList<>();
        List<String> duplicates = findDuplicates(links.getAllLinks());

        for (Future<LinkCheck> future : futures) {
            try {
                LinkCheck check = future.get();
                if (!check.isValid()) {
                    brokenLinks.add(formatBrokenLink(check));
                }
            } catch (Exception e) {
                log.error("Link check failed: {}", e.getMessage());
                brokenLinks.add("Error checking link: " + e.getMessage());
            }
        }

        result.put("broken_links", brokenLinks);
        result.put("duplicate_links", duplicates);
        return result;
    }

    private String formatBrokenLink(LinkCheck check) {
        return String.format("%s (Status: %d %s)",
                check.url(), check.status(), check.message());
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
            return new LinkCheck(url, 500, "Connection failed");
        }
    }

    private List<String> findDuplicates(List<String> allLinks) {
        Map<String, Integer> urlCounts = new HashMap<>();
        List<String> duplicates = new ArrayList<>();

        for (String url : allLinks) {
            urlCounts.put(url, urlCounts.getOrDefault(url, 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : urlCounts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicates.add(formatDuplicate(entry.getKey(), entry.getValue()));
            }
        }

        return duplicates;
    }

    private String formatDuplicate(String url, int count) {
        return String.format("%s appears %d times", url, count);
    }

    private record LinkCheck(String url, int status, String message) {
        boolean isValid() {
            return status == 200 || status == 301 || status == 302;
        }
    }
}