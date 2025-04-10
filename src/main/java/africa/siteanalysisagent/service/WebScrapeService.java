package africa.siteanalysisagent.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class WebScrapeService {
    private final ConcurrentMap<String, Document> scrapeCache = new ConcurrentHashMap<>();

    public Document scrape(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        return scrapeCache.computeIfAbsent(url, k -> {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(10000)
                        .get();
            } catch (IOException e) {
                throw new RuntimeException("Failed to scrape URL: " + url, e);
            }
        });
    }

    public Document scrapeWithRetry(String url, int maxRetries) throws IOException, InterruptedException {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        int retries = 0;
        while (retries < maxRetries) {
            try {
                return scrape(url);
            } catch (RuntimeException e) {
                if (++retries == maxRetries) {
                    throw new IOException("Failed after " + maxRetries + " attempts", e);
                }
                Thread.sleep(1000 * retries);
            }
        }
        throw new IOException("Failed to scrape URL: " + url);
    }
}