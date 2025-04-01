        package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.*;
import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.SEOReport;
import africa.siteanalysisagent.model.UserSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    // Main Analysis Method ==============================================
    public SEOAnalysisResult analyzeSEO(String url, Map<String, List<String>> metaTags, CategorizedLink links) {
        try {
            // 1. Calculate comprehensive score
            int score = calculateSeoScore(metaTags, links, url);

            // 2. Get detailed analysis from Gemini
            String prompt = buildFullAnalysisPrompt(url, metaTags, links);
            String analysisText = callGeminiAPI(prompt);

            // 3. Parse structured response
            Map<String, String> parsedAnalysis = parseFullAnalysis(analysisText);

            // 4. Return unified result
            return SEOAnalysisResult.success(
                    score,
                    parsedAnalysis.get("strengths"),
                    parsedAnalysis.get("issues"),
                    parsedAnalysis.get("recommendations"),
                    parsedAnalysis.get("optimizedTags"),
                    parsedAnalysis.get("technical"),
                    parsedAnalysis.get("quickFixes")
            );

        } catch (Exception e) {
            log.error("SEO analysis failed for {}", url, e);
            return SEOAnalysisResult.error("Analysis failed: " + e.getMessage());
        }
    }

    public String generateReportSummary(SEOReport report) {
        String prompt = String.format("""
            Generate a concise 3-4 sentence summary of this SEO report:
            
            URL: %s
            Score: %d/100
            Key Issues: %s
            Recommendations: %s
            
            Focus on the most critical findings and keep it very brief.
            """,
                report.getUrl(),
                report.getScore(),
                report.getRecommendations(),
                report.getOptimizedMetaTags()
        );

        try {
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            log.error("Failed to generate report summary", e);
            return "Summary unavailable - see full report for details";
        }
    }

    public String answerReportQuestion(SEOReport report, String question, List<ChatMessage> chatHistory) {
        String prompt = buildQuestionPrompt(report, question, chatHistory);
        try {
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            log.error("Failed to answer report question", e);
            return "I couldn't process that question. Please try rephrasing.";
        }
    }

    public String suggestFixes(SEOReport report, String request, List<ChatMessage> chatHistory) {
        String prompt = String.format("""
            Based on this SEO report and user request, provide specific fixes:
            
            Report:
            - URL: %s
            - Score: %d/100
            - Issues: %s
            
            User Request: %s
            Chat Context: %s
            
            Provide actionable fixes in a numbered list, ordered by impact.
            Include estimated difficulty (Easy/Medium/Hard) for each.
            """,
                report.getUrl(),
                report.getScore(),
                report.getRecommendations(),
                request,
                formatChatHistory(chatHistory)
        );

        try {
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            log.error("Failed to generate fixes", e);
            return "Fix suggestions unavailable - try asking about specific issues";
        }
    }

    public String analyzeBrokenLinks(SEOReport report,int brokenLinksCount,List<String> brokenLinksDetails,
             String query, List<ChatMessage> chatHistory) throws ExecutionException, InterruptedException {

            String prompt = String.format("""
                            Analyze broken links for this site based on user query:
                                        
                            Site: %s
                            Broken Links: %d
                            User Query: %s
                            Chat Context: %s
                                        
                            Provide:
                            1. Severity assessment
                            2. Most critical broken links
                            3. Recommended fixes
                            """,
                    report.getUrl(),
                    brokenLinksCount,
                    String.join(", ", brokenLinksDetails.subList(0, Math.min(3, brokenLinksDetails.size()))),

                    query,
                    formatChatHistory(chatHistory)
            );
            return callGeminiAPI(prompt);
    }

    public String provideTechAdvice(String query, SEOReport report, List<ChatMessage> chatHistory) {
        String context = report != null ?
                String.format("Current site: %s (Score: %d)", report.getUrl(), report.getScore()) :
                "No specific site analysis available";

        String prompt = String.format("""
            Provide technical SEO advice based on:
            
            User Query: %s
            %s
            Chat Context: %s
            
            Answer with:
            1. Clear explanation
            2. Implementation steps
            3. Expected impact
            """,
                query,
                context,
                formatChatHistory(chatHistory)
        );

        try {
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            log.error("Failed to provide tech advice", e);
            return "Technical advice unavailable - please try again";
        }
    }

    public String answerGeneralSeoQuestion(String question, List<ChatMessage> chatHistory) {
        String prompt = String.format("""
            Answer this general SEO question knowledgeably:
            
            Question: %s
            Context: %s
            
            Provide:
            1. Concise explanation
            2. Best practices
            3. Common mistakes to avoid
            """,
                question,
                formatChatHistory(chatHistory)
        );

        try {
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            log.error("Failed to answer SEO question", e);
            return "I couldn't answer that - please try rephrasing your question";
        }
    }

    // ============== Helper Methods ==============

    private String buildQuestionPrompt(SEOReport report, String question, List<ChatMessage> chatHistory) {
        return String.format("""
            Answer this specific question about the SEO report:
            
            Report Summary:
            - URL: %s
            - Score: %d/100
            - Key Issues: %s
            
            Question: %s
            Chat Context: %s
            
            Provide a focused answer with:
            1. Direct response
            2. Supporting evidence from report
            3. Suggested next steps
            """,
                report.getUrl(),
                report.getScore(),
                report.getRecommendations(),
                question,
                formatChatHistory(chatHistory)
        );
    }

    private String formatChatHistory(List<ChatMessage> chatHistory) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return "No previous conversation context";
        }

        StringBuilder sb = new StringBuilder("Previous conversation:\n");
        for (ChatMessage message : chatHistory) {
            if (message.getUserMessage() != null) {
                sb.append("User: ").append(message.getUserMessage()).append("\n");
            }
            if (message.getBotResponse() != null) {
                sb.append("Bot: ").append(message.getBotResponse()).append("\n");
            }
        }
        return sb.toString();
    }

    // Scoring Calculation ===============================================
    private int calculateSeoScore(Map<String, List<String>> metaTags, CategorizedLink links, String baseUrl) {
        int score = 0;

        // On-Page SEO (40%)
        score += calculateOnPageScore(metaTags);

        // Technical SEO (30%)
        score += calculateTechnicalScore(links, baseUrl);

        // Content Quality (20%)
        score += calculateContentScore(metaTags, links);

        // User Experience (10%)
        score += calculateUserExperienceScore(links);

        return Math.min(score, 100);
    }

    private int calculateOnPageScore(Map<String, List<String>> metaTags) {
        int score = 0;
        if (hasCompleteMetaTags(metaTags)) score += 15;
        if (hasGoodKeywords(metaTags)) score += 10;
        if (hasProperHeadings(metaTags)) score += 10;
        if (hasMobileViewport(metaTags)) score += 5;
        return score;
    }

    private int calculateTechnicalScore(CategorizedLink links, String baseUrl) {
        int score = 0;
        if (links.getInternalLinkCount(baseUrl) > 15) score += 10;
        if (!links.getBacklinks().isEmpty()) score += 8;
        if (links.getResourceLinkCount() < 20) score += 7;
        if (links.getStylesheetLinks().size() < 3) score += 5;
        return score;
    }

    private int calculateContentScore(Map<String, List<String>> metaTags, CategorizedLink links) {
        int score = 0;
        if (hasGoodDescription(metaTags)) score += 10;
        if (!links.getBreadcrumbLinks().isEmpty()) score += 5;
        if (links.getSidebarLinks().size() > 2) score += 5;
        return score;
    }

    private int calculateUserExperienceScore(CategorizedLink links) {
        int score = 0;
        if (links.getNavigationLinks().size() >= 5) score += 5;
        if (!links.getFooterLinks().isEmpty()) score += 3;
        if (!links.getSocialMediaLinks().isEmpty()) score += 2;
        return score;
    }

    // Validation Helpers ================================================
    private boolean hasCompleteMetaTags(Map<String, List<String>> metaTags) {
        return metaTags.containsKey("title") &&
                metaTags.containsKey("description");
    }

    private boolean hasGoodKeywords(Map<String, List<String>> metaTags) {
        return metaTags.getOrDefault("keywords", List.of()).size() > 0;
    }

    private boolean hasProperHeadings(Map<String, List<String>> metaTags) {
        return metaTags.containsKey("h1");
    }

    private boolean hasMobileViewport(Map<String, List<String>> metaTags) {
        return metaTags.getOrDefault("viewport", List.of())
                .stream()
                .anyMatch(v -> v.contains("width=device-width"));
    }

    private boolean hasGoodDescription(Map<String, List<String>> metaTags) {
        return metaTags.getOrDefault("description", List.of())
                .stream()
                .anyMatch(d -> d.length() > 100);
    }

    // Prompt Engineering ================================================
    private String buildFullAnalysisPrompt(String url, Map<String, List<String>> metaTags, CategorizedLink links) {
        return String.format("""
            Analyze SEO for: %s
            
            Meta Tags:
            %s
            
            Link Structure:
            - Navigation: %d
            - Footer: %d
            - Breadcrumbs: %d
            - Outbound: %d
            - Backlinks: %d
            - Social: %d
            - Images: %d
            - Scripts: %d
            - CSS: %d
            - Total Internal Links: %d
            - Total External Links: %%d
            - Total Resource Links: %d
            - All Links: %d
            
            Provide structured analysis with:
            [STRENGTHS]
            [ISSUES]
            [RECOMMENDATIONS]
            [OPTIMIZED_TAGS]
            [TECHNICAL]
            [QUICK_FIXES]
            """,
                url,
                formatMetaTags(metaTags),
                links.getNavigationLinks().size(),
                links.getFooterLinks().size(),
                links.getBreadcrumbLinks().size(),
                links.getOutboundLinks().size(),
                links.getBacklinks().size(),
                links.getSocialMediaLinks().size(),
                links.getImageLinks().size(),
                links.getScriptLinks().size(),
                links.getStylesheetLinks().size(),
                links.getInternalLinks(url),
                links.getExternalLinks(url),
                links.getAllLinks()
        );
    }

    private String formatMetaTags(Map<String, List<String>> metaTags) {
        StringBuilder sb = new StringBuilder();
        metaTags.forEach((key, values) -> {
            sb.append("- ").append(key).append(": ");
            sb.append(values.size() == 1 ? values.get(0) : values);
            sb.append("\n");
        });
        return sb.toString();
    }

    // Response Processing ===============================================
    private Map<String, String> parseFullAnalysis(String analysisText) {
        Map<String, String> result = new HashMap<>();
        String[] sections = analysisText.split("\\[([A-Z_]+)\\]");

        for (int i = 1; i < sections.length; i += 2) {
            String sectionName = sections[i];
            String content = (i + 1 < sections.length) ? sections[i+1].trim() : "";
            result.put(sectionName, content);
        }

        return result;
    }

    // Core API Handling =================================================
    private String callGeminiAPI(String prompt) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                HttpEntity<Map<String, Object>> request = createRequest(prompt);
                ResponseEntity<String> response = restTemplate.exchange(
                        GEMINI_API_URL + "?key=" + apiKey,
                        HttpMethod.POST,
                        request,
                        String.class
                );
                return extractResponseText(response.getBody());
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("Rate limited, retrying...");
                sleep(RETRY_DELAY_MS);
            } catch (Exception e) {
                log.error("API call failed", e);
                break;
            }
        }
        throw new RuntimeException("Failed after " + MAX_RETRIES + " retries");
    }

    private String extractResponseText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();
    }

    private HttpEntity<Map<String, Object>> createRequest(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(
                Map.of("parts", List.of(
                        Map.of("text", prompt)
                ))
        ));
        requestBody.put("generationConfig", Map.of(
                "maxOutputTokens", 2000,
                "temperature", 0.7
        ));

        return new HttpEntity<>(requestBody, headers);
    }


    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignored) {}
    }

}