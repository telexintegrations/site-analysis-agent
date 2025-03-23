package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class GeminiService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}") // Inject the Gemini API key from configuration
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private ResponseEntity<String> sendGeminiRequest(HttpEntity<Map<String, Object>> request) {
        int retryCount = 3; // Maximum retries
        int delayMillis = 5000; // Wait time before retrying (5 seconds)

        for (int i = 0; i < retryCount; i++) {
            try {
                return restTemplate.exchange(GEMINI_API_URL + "?key=" + apiKey, HttpMethod.POST, request, String.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("⚠️ Gemini API Rate Limit Exceeded (429). Retrying in {} ms...", delayMillis);
                try {
                    Thread.sleep(delayMillis); // Wait before retrying
                } catch (InterruptedException ignored) {}
            }
        }

        throw new RuntimeException("❌ Gemini API quota exceeded. Please check your plan.");
    }

    public Map<String, Object> analyzeSeo(String url, Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        try {
            // Step 1: Calculate SEO score
            int seoScore = calculateSeoScore(metaTags, categorizedLinks);

            // Step 2: Generate SEO recommendations using Gemini
            Map<String, String> recommendationsResult = generateSeoRecommendations(url, metaTags, categorizedLinks);
            String recommendations = recommendationsResult.getOrDefault("recommendations", "No recommendations found.");

            // Step 3: Generate optimized meta tags separately
            Map<String, String> optimizedMetaTagsResult = generateOptimizedMetaTags(url, metaTags, categorizedLinks);
            String optimizedMetaTags = optimizedMetaTagsResult.getOrDefault("optimized_meta_tags", "No optimized meta tags found.");

            // Step 4: Combine results
            Map<String, Object> result = new HashMap<>();
            result.put("seo_score", seoScore);
            result.put("optimization_suggestions", recommendations);
            result.put("optimized_meta_tags", optimizedMetaTags);

            return result;

        } catch (Exception e) {
            log.error("❌ Error calling Gemini API: {}", e.getMessage(), e);
            return Map.of("error", "Failed to analyze content with Gemini.");
        }
    }

    public Map<String, String> generateSeoRecommendations(String url, Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        try {
            // Build the prompt specifically for generating SEO recommendations
            String prompt = buildRecommendationPrompt(url, metaTags, categorizedLinks);

            // Send the request to Gemini
            HttpEntity<Map<String, Object>> request = createRequest(prompt);
            ResponseEntity<String> response = sendGeminiRequest(request);

            // Parse the response
            Map<String, Object> geminiResponse = parseGeminiResponse(response.getBody());

            // Extract the SEO recommendations
            String recommendations = (String) geminiResponse.getOrDefault("optimization_suggestions", "No recommendations found.");

            // Return the result
            Map<String, String> result = new HashMap<>();
            result.put("recommendations", recommendations);
            return result;

        } catch (Exception e) {
            log.error("❌ Error generating SEO recommendations: {}", e.getMessage(), e);
            return Map.of("error", "Failed to generate SEO recommendations.");
        }
    }

    public Map<String, String> generateOptimizedMetaTags(String url, Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        try {
            // Build the prompt specifically for generating optimized meta tags
            String prompt = buildOptimizationPrompt(url, metaTags, categorizedLinks);

            // Send the request to Gemini
            HttpEntity<Map<String, Object>> request = createRequest(prompt);
            ResponseEntity<String> response = sendGeminiRequest(request);

            // Parse the response
            Map<String, Object> geminiResponse = parseGeminiResponse(response.getBody());

            // Extract the optimized meta tags
            String optimizedMetaTags = (String) geminiResponse.getOrDefault("optimized_meta_tags", "No optimized meta tags found.");

            // Return the result
            Map<String, String> result = new HashMap<>();
            result.put("optimized_meta_tags", optimizedMetaTags);
            return result;

        } catch (Exception e) {
            log.error("❌ Error generating optimized meta tags: {}", e.getMessage(), e);
            return Map.of("error", "Failed to generate optimized meta tags.");
        }
    }

    private String buildRecommendationPrompt(String url, Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an SEO expert. Analyze the following website and provide detailed recommendations for improvement:\n\n");
        prompt.append("🔗 **Website URL:** ").append(url).append("\n\n");

        prompt.append("🏷️ **Current Meta Tags:**\n");
        metaTags.forEach((key, value) -> prompt.append(" - ").append(key).append(": ").append(value).append("\n"));

        prompt.append("\n🔗 **Current Links:**\n");
        prompt.append(" - Navigation Links: ").append(categorizedLinks.navigationLinks().size()).append("\n");
        prompt.append(" - Outbound Links: ").append(categorizedLinks.outboundLinks().size()).append("\n");
        prompt.append(" - Backlinks: ").append(categorizedLinks.backlinks().size()).append("\n");
        prompt.append(" - Social Media Links: ").append(categorizedLinks.socialMediaLinks().size()).append("\n");
        prompt.append(" - Affiliate Links: ").append(categorizedLinks.affiliateLinks().size()).append("\n");

        // 📌 **Force Gemini to Generate SEO Recommendations**
        prompt.append("\n💡 Provide key SEO recommendations to improve page ranking, structured like this:\n");
        prompt.append("```\n");
        prompt.append("Suggestions:\n");
        prompt.append("- [Your first suggestion]\n");
        prompt.append("- [Your second suggestion]\n");
        prompt.append("- [Your third suggestion]\n");
        prompt.append("- [Your fourth suggestion]\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    private String buildOptimizationPrompt(String url, Map<String, List<String>> metaTags, CategorizedLink categorizedLinks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an SEO expert. Analyze the following website and generate optimized meta tags:\n\n");
        prompt.append("🔗 **Website URL:** ").append(url).append("\n\n");

        prompt.append("🏷️ **Current Meta Tags:**\n");
        metaTags.forEach((key, value) -> prompt.append(" - ").append(key).append(": ").append(value).append("\n"));

        prompt.append("\n🔗 **Current Links:**\n");
        prompt.append(" - Navigation Links: ").append(categorizedLinks.navigationLinks().size()).append("\n");
        prompt.append(" - Outbound Links: ").append(categorizedLinks.outboundLinks().size()).append("\n");
        prompt.append(" - Backlinks: ").append(categorizedLinks.backlinks().size()).append("\n");
        prompt.append(" - Social Media Links: ").append(categorizedLinks.socialMediaLinks().size()).append("\n");
        prompt.append(" - Affiliate Links: ").append(categorizedLinks.affiliateLinks().size()).append("\n");

        // 📌 **Force Gemini to Generate Optimized Meta Tags**
        prompt.append("\n📊 **Generate optimized meta tags** for better SEO, structured like this:\n");
        prompt.append("```\n");
        prompt.append("Title: Your optimized title here\n");
        prompt.append("Meta Description: Your optimized description here\n");
        prompt.append("Keywords: keyword1, keyword2, keyword3\n");
        prompt.append("Canonical: https://your-optimized-url.com\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    private HttpEntity<Map<String, Object>> createRequest(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        requestBody.put("generationConfig", Map.of("maxOutputTokens", 1000));

        return new HttpEntity<>(requestBody, headers);
    }

    private Map<String, Object> parseGeminiResponse(String responseBody) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        // Log the raw response for debugging
        log.info("Full Gemini API Response: {}", jsonNode.toPrettyString());

        // Extract the generated text
        String textResponse = jsonNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        // Parse the Gemini response to extract suggestions and optimized meta tags
        Map<String, Object> result = new HashMap<>();
        result.put("optimization_suggestions", extractSuggestions(textResponse));
        result.put("optimized_meta_tags", extractOptimizedMetaTags(textResponse));

        return result;
    }

    private String extractSuggestions(String textResponse) {
        if (textResponse.contains("Suggestions:")) {
            return textResponse.split("Suggestions:")[1].trim();
        } else if (textResponse.contains("Recommendations:")) {
            return textResponse.split("Recommendations:")[1].trim();
        } else if (textResponse.contains("Analysis:")) {
            return textResponse.split("Analysis:")[1].trim();
        }
        return "No suggestions found.";
    }

    private String extractOptimizedMetaTags(String textResponse) {
        if (textResponse.contains("Title:") || textResponse.contains("Meta Description:")) {
            String[] lines = textResponse.split("\n");
            StringBuilder metaTags = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("Title:") || line.startsWith("Meta Description:") || line.startsWith("Keywords:") || line.startsWith("Canonical:")) {
                    metaTags.append(line).append("\n");
                }
            }
            return metaTags.toString().isEmpty() ? "No optimized meta tags found." : metaTags.toString();
        }
        return "No optimized meta tags found.";
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
}