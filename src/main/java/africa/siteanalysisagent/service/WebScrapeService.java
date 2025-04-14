package africa.siteanalysisagent.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class WebScrapeService {
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final Set<String> INVALID_SCHEMES = Set.of("mailto", "tel", "javascript");
    private static final int TIMEOUT_MS = 15000;

    public String scrapeWithRetry(String url) throws IOException, InterruptedException {
        return scrapeWithRetry(url, DEFAULT_MAX_RETRIES);
    }

    public String scrapeWithRetry(String url, int maxRetries) throws IOException, InterruptedException {
        if (!isValidUrlForScraping(url)) {
            throw new IllegalArgumentException("Invalid URL for scraping: " + url);
        }

        int attempts = 0;
        IOException lastException = null;

        while (attempts < maxRetries) {
            try {
                return scrape(url);
            } catch (IOException e) {
                lastException = e;
                attempts++;
                log.warn("Scrape attempt {} failed for {}: {}", attempts, url, e.getMessage());

                if (attempts < maxRetries) {
                    Thread.sleep(RETRY_DELAY_MS * attempts);
                }
            }
        }

        throw new IOException("Failed after " + maxRetries + " attempts", lastException);
    }

    boolean isValidUrlForScraping(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String path = uri.getPath();

            if (scheme == null || INVALID_SCHEMES.contains(scheme.toLowerCase())) {
                return false;
            }

            if (path != null && (path.matches(".*\\.(pdf|docx?|xlsx?|jpg|jpeg|png|gif|zip|rar)$"))) {
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String scrape(String url) throws IOException {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; SiteAnalysisAgent/1.0)")
                    .followRedirects(true)
                    .maxBodySize(0) // No limit
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            if (response.statusCode() >= 400) {
                throw new IOException("HTTP error " + response.statusCode() + " for URL: " + url);
            }

            return response.parse().html();
        } catch (Exception e) {
            throw new IOException("Failed to scrape URL: " + url, e);
        }
    }
}