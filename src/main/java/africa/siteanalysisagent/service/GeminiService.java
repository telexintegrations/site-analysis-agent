package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class GptService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gpt.api.key}")
    private String apiKey;

    private static final String GPT_API_URL = "https://api.openai.com/v1/chat/completions";

    private ResponseEntity<String> sendGptRequest(HttpEntity<Map<String, Object>> request) {
        int retryCount = 3; // Maximum retries
        int delayMillis = 5000; // Wait time before retrying (5 seconds)

        for (int i = 0; i < retryCount; i++) {
            try {
                return restTemplate.exchange(GPT_API_URL, HttpMethod.POST, request, String.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("⚠️ OpenAI Rate Limit Exceeded (429). Retrying in {} ms...", delayMillis);
                try {
                    Thread.sleep(delayMillis); // Wait before retrying
                } catch (InterruptedException ignored) {}
            }
        }

        throw new RuntimeException("❌ OpenAI API quota exceeded. Please check your plan.");
    }

    public Map<String, Object> analyzeSeo(String url, Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        try {
            // Step 1: Calculate SEO score
            int seoScore = calculateSeoScore(metaTags, categorizedLinks);

            // Step 2: Generate optimization suggestions using GPT
            String prompt = buildPrompt(url, metaTags, categorizedLinks);
            HttpEntity<Map<String, Object>> request = createRequest(prompt);
            ResponseEntity<String> response = restTemplate.exchange(GPT_API_URL, HttpMethod.POST, request, String.class);

            // Step 3: Parse GPT response
            Map<String, Object> gptResponse = parseGPTResponse(response.getBody());

            // Step 4: Combine results
            Map<String, Object> result = new HashMap<>();
            result.put("seo_score", seoScore);
            result.put("optimization_suggestions", gptResponse.get("optimization_suggestions"));
            result.put("optimized_meta_tags", gptResponse.get("optimized_meta_tags"));

            return result;

        } catch (Exception e) {
            log.error("❌ Error calling GPT API: {}", e.getMessage(), e);
            return Map.of("error", "Failed to analyze content with GPT.");
        }
    }

    private int calculateSeoScore(Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        int score = 0;

        // On-Page SEO (40%)
        score += calculateOnPageSeoScore(metaTags, categorizedLinks);

        // Off-Page SEO (30%)
        score += calculateOffPageSeoScore(categorizedLinks);

        // Technical SEO (20%)
        score += calculateTechnicalSeoScore(metaTags);

        // User Experience (10%)
        score += calculateUserExperienceScore(categorizedLinks);

        return Math.min(score, 100); // Ensure score doesn't exceed 100
    }

    /**
     * Calculates the On-Page SEO score (40%).
     */
    private int calculateOnPageSeoScore(Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        int score = 0;

        // Content Quality (15%)
        if (metaTags.containsKey("🔍 *Core SEO Tags*")) {
            score += 15; // Max 15 points for core SEO tags
        }

        // Keyword Optimization (10%)
        if (metaTags.containsKey("🔑 **Meta Keywords**")) {
            score += 10; // Max 10 points for keyword optimization
        }

        // Meta Tags & Structured Data (5%)
        if (metaTags.containsKey("🕷️ *Crawling & Indexing Tags*")) {
            score += 5; // Max 5 points for meta tags and structured data
        }

        // Internal Linking & Anchor Text (5%)
        if (!categorizedLinks.navigationLinks().isEmpty()) {
            score += 5; // Max 5 points for internal linking
        }

        return score;
    }

    /**
     * Calculates the Off-Page SEO score (30%).
     */
    private int calculateOffPageSeoScore(CategorizedLink categorizedLinks) {
        int score = 0;

        // Backlinks & Domain Authority (20%)
        if (!categorizedLinks.backlinks().isEmpty()) {
            score += 20; // Max 20 points for backlinks
        }

        // Social Media & Brand Mentions (5%)
        if (!categorizedLinks.socialMediaLinks().isEmpty()) {
            score += 5; // Max 5 points for social media links
        }

        return score;
    }

    /**
     * Calculates the Technical SEO score (20%).
     */
    private int calculateTechnicalSeoScore(Map<String, List<String>> metaTags) {
        int score = 0;

        // Website Speed & Performance (10%)
        if (metaTags.containsKey("📱 *Author & Mobile Optimization*")) {
            score += 10; // Max 10 points for mobile optimization
        }

        // Crawlability & Indexing (5%)
        if (metaTags.containsKey("🕷️ *Crawling & Indexing Tags*")) {
            score += 5; // Max 5 points for crawlability
        }

        return score;
    }

    /**
     * Calculates the User Experience score (10%).
     */
    private int calculateUserExperienceScore(CategorizedLink categorizedLinks) {
        int score = 0;

        // User-Friendly Design (5%)
        if (!categorizedLinks.navigationLinks().isEmpty()) {
            score += 5; // Max 5 points for navigation links
        }

        // Bounce Rate & Dwell Time (5%)
        if (!categorizedLinks.outboundLinks().isEmpty()) {
            score += 5; // Max 5 points for outbound links
        }

        return score;
    }


    private String buildPrompt(String url, Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the SEO of this website and provide detailed recommendations for improvement:\n\n");
        prompt.append("🔗 URL: ").append(url).append("\n\n");

        prompt.append("🏷️ **Current Meta Tags:**\n");
        metaTags.forEach((key, value) -> prompt.append(" - ").append(key).append(": ").append(value).append("\n"));

        prompt.append("\n🔗 **Current Links:**\n");
        prompt.append(" - Navigation Links: ").append(categorizedLinks.navigationLinks().size()).append("\n");
        prompt.append(" - Outbound Links: ").append(categorizedLinks.outboundLinks().size()).append("\n");
        prompt.append(" - Backlinks: ").append(categorizedLinks.backlinks().size()).append("\n");
        prompt.append(" - Social Media Links: ").append(categorizedLinks.socialMediaLinks().size()).append("\n");
        prompt.append(" - Affiliate Links: ").append(categorizedLinks.affiliateLinks().size()).append("\n");

        prompt.append("\n📊 **Provide detailed SEO recommendations and optimized meta tags.**\n");
        return prompt.toString();
    }


    private HttpEntity<Map<String, Object>> createRequest(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-3.5-turbo");
        requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        requestBody.put("max_tokens", 300);

        return new HttpEntity<>(requestBody, headers);
    }


    private Map<String, Object> parseGPTResponse(String responseBody) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String textResponse = jsonNode.path("choices").get(0).path("message").path("content").asText();

        // Parse the GPT response to extract suggestions and optimized meta tags
        Map<String, Object> result = new HashMap<>();
        result.put("optimization_suggestions", extractSuggestions(textResponse));
        result.put("optimized_meta_tags", extractOptimizedMetaTags(textResponse));

        return result;
    }



    private String extractSuggestions(String textResponse) {
        if (textResponse.contains("Suggestions:")) {
            return textResponse.split("Suggestions:")[1].trim();
        }
        return "No suggestions found.";
    }


    private String extractOptimizedMetaTags(String textResponse) {
        if (textResponse.contains("Optimized Meta Tags:")) {
            return textResponse.split("Optimized Meta Tags:")[1].trim();
        }
        return "No optimized meta tags found.";
    }
}