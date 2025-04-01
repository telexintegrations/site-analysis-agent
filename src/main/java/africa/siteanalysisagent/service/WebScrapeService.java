package africa.siteanalysisagent.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebScrapeService {
    private static final int TIMEOUT = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private final Map<String, Document> documentCache = new ConcurrentHashMap<>();

    public Document scrape(String url) throws IOException {
        // Check cache first
        if (documentCache.containsKey(url)) {
            return documentCache.get(url);
        }

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .get();

        // Cache the result
        documentCache.put(url, doc);
        return doc;
    }

    public Document scrapeWithRetry(String url, int maxRetries) throws IOException, InterruptedException {
        int retryCount = 0;
        IOException lastException = null;

        while (retryCount < maxRetries) {
            try {
                return scrape(url);
            } catch (IOException e) {
                lastException = e;
                retryCount++;
                Thread.sleep(500); // Wait before retrying
            }
        }
        throw new IOException("Failed to scrape URL after " + maxRetries + " retries: " + url, lastException);
    }

    public String scrapeHtml(String url) throws IOException {
        return scrape(url).html();
    }

    public String scrapeText(String url) throws IOException {
        return scrape(url).text();
    }
}

