package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.SiteAnalysis;
import africa.siteanalysisagent.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class LynxServiceImpl implements LynxService {

    private final MetaAnalysisService metaAnalysisService;
    private GeminiService geminiService;
    private final TelexService telexService; // Already configured with channels
    private final BrokenLinkAndDuplicateTracker brokenLinkAndDuplicateTracker;


    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();


    @Override
    public ChatResponse processMessage(ChatMessage chatMessage) {
        String userId = chatMessage.getUserId();
        String userMessage = chatMessage.getUserMessage();

        UserSession session = userSessions.computeIfAbsent(userId, k -> new UserSession(userId));
        session.updateLastActivity();

        try {
            // Step 1: Analyze intent (using your existing method)
            UserIntent intent = analyzeIntent(userMessage, session);

            // Step 2: Process based on intent
            ChatResponse response = switch (intent.getType()) {
                case NEW_ANALYSIS -> processAnalysisRequest(userId, userMessage, intent.getUrl(), session);
                case REPORT_QUESTION -> answerReportQuestion(userId, userMessage, session);
                case FIX_SUGGESTION -> provideFixSuggestions(userId, userMessage, session);
                case TECH_ADVICE -> provideTechnicalAdvice(userMessage, session);
                case GENERAL_SEO -> provideGeneralSeoAdvice(userMessage, session);
                case BROKEN_LINK_HELP -> handleBrokenLinkQuery(userId, userMessage, session);
                default -> handleUnsupportedQuery();
            };

            // Step 3: Update session with bot response
            session.addMessage(new ChatMessage(
                    userId,
                    userMessage,
                    response.getMessage(),
                    LocalDateTime.now()
            ));

            // Step 4: Send to Telex (if configured)
            notifyTelex(session, userMessage, response);

            return response;

        } catch (Exception e) {
            log.error("Message processing failed for user {}", userId, e);
            notifyTelexError(userId, userMessage, e, session.getChannelId());
            return ChatResponse.error("I encountered an error. Please try again.");
        }
    }

    @Override
    public ChatResponse chat(String userId, String userMessage) {
        UserSession session = userSessions.computeIfAbsent(userId, k -> new UserSession(userId));

        session.updateLastActivity();

        try {
            // Step 1: Analyze user intent
            UserIntent intent = analyzeIntent(userMessage, session);



            ChatResponse response = switch (intent.getType()) {
                case NEW_ANALYSIS -> processAnalysisRequest(userId, userMessage, intent.getUrl(), session);
                case REPORT_QUESTION -> answerReportQuestion(userId, userMessage, session);
                case FIX_SUGGESTION -> provideFixSuggestions(userId, userMessage, session);
                case TECH_ADVICE -> provideTechnicalAdvice(userMessage, session);
                case GENERAL_SEO -> provideGeneralSeoAdvice(userMessage, session);
                case BROKEN_LINK_HELP -> handleBrokenLinkQuery(userId, userMessage, session);
                default -> handleUnsupportedQuery();
            };

            notifyTelex(session, userMessage, response);
            return response;
        } catch (Exception e) {
            log.error("Chat processing failed for user {}", userId, e);
            return ChatResponse.error("I encountered an error. Please try again.");
        }
    }
    private void notifyTelex(UserSession session, String userMessage, ChatResponse response) {
        String channelId = session.getChannelId();
        if (channelId == null) {
            return; // Skip if no channel configured
        }

        String message = String.format("""
            💬 [Lynx Interaction]
            ━━━━━━━━━━━━━━━            notifyTelexError(userId, userMessage, e,);
                           
            User: %s
            Intent: %s
            Query: %s
            Response: %s
            Context: %s
            ━━━━━━━━━━━━━━━
            """,
                session.getUserId(),
                response.getType(),
                truncate(userMessage, 120),
                truncate(response.getMessage(), 240),
                session.getCurrentUrl() != null ? session.getCurrentUrl() : "N/A"
        );

        telexService.sendMessage(channelId, message)
                .exceptionally(e -> {
                    log.warn("Failed to send to Telex channel {}", channelId, e);
                    return null;
                });
    }

    private void notifyTelexError(String userId, String userMessage, Exception e, String channelId) {
        if (channelId == null) {
            return;
        }

        String errorMsg = String.format("""
            ‼️ [Lynx Error]
            ━━━━━━━━━━━━━━━
            User: %s
            Query: %s
            Error: %s
            ━━━━━━━━━━━━━━━
            """,
                userId,
                truncate(userMessage, 200),
                e.getMessage()
        );

        telexService.sendMessage(channelId, errorMsg)
                .exceptionally(ex -> {
                    log.warn("Failed to send error to Telex channel {}", channelId, ex);
                    return null;
                });
    }

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    // Intent Analysis ==============================================
    private UserIntent analyzeIntent (String message, UserSession session){
        String lowerMsg = message.toLowerCase().trim();

        // Check for analysis requests
        if (lowerMsg.matches(".*(analyze|scan|check|test)\\s+(http|www|\\.[a-z]{2,}).*")) {
            return new UserIntent(UserIntent.IntentType.NEW_ANALYSIS, extractUrl(message));
        }

        // Check for report-related questions
        if (session.hasActiveReport()) {
            if (lowerMsg.matches(".*(score|rating|grade).*")) {
                return new UserIntent(UserIntent.IntentType.REPORT_QUESTION, null);
            }
            if (lowerMsg.matches(".*(fix|improve|problem|issue|broken).*")) {
                return new UserIntent(UserIntent.IntentType.FIX_SUGGESTION, null);
            }
            if (lowerMsg.matches(".*(link|url|404|error).*")) {
                return new UserIntent(UserIntent.IntentType.BROKEN_LINK_HELP, null);
            }
        }

        // Technology questions
        if (lowerMsg.matches(".*(tech|framework|library|tool|optimize|speed).*")) {
            return new UserIntent(UserIntent.IntentType.TECH_ADVICE, null);
        }

        // General SEO
        if (lowerMsg.matches(".*(seo|search engine|ranking|traffic|google).*")) {
            return new UserIntent(UserIntent.IntentType.GENERAL_SEO, null);
        }

        return new UserIntent(UserIntent.IntentType.UNSUPPORTED, null);
    }


    // Handlers
    private ChatResponse processAnalysisRequest(String userId, String message, String url, UserSession session){
        try {

            // perform analysis
            SiteAnalysis analysis = metaAnalysisService.analyzeSite(url);
            SEOReport report = metaAnalysisService.generateFullReport(url);

            //update session
            session.setCurrentAnalysis(analysis);
            session.setCurrentReport(report);
            session.addMessage(new ChatMessage(userId, message, null, LocalDateTime.now()));

            //generate AI Summary
            String summary = geminiService.generateReportSummary(report);
            session.addMessage(new ChatMessage(userId, null, summary, LocalDateTime.now()));

            return ChatResponse.forAnalysis(
                    report,
                    "\uD83D\uDD0D Analysis Completed!\n" + summary
            );
        } catch (IOException e) {
            log.error("Analysis failed for URL: {}", url, e);
            return ChatResponse.error("Failed to analyze " + url + ". Please check the URL and try again.");
        }
    }

    private ChatResponse answerReportQuestion(String userId, String message, UserSession session){
        if(!session.hasActiveReport()){
            return ChatResponse.prompt("Please analyze a website first using: 'analyze example.com'");
        }
        try {
            String response = geminiService.answerReportQuestion(
                    session.getCurrentReport(),
                    message,
                    session.getChatHistory()
            );

            session.addMessage(new ChatMessage(userId, message, response, LocalDateTime.now()));

            return ChatResponse.forReportQuestion(
                    response,
                    session.getCurrentReport()
            );
        }catch (Exception e){
            log.error("Failed to answer report question", e);
            return ChatResponse.error("I couldn't process that question about your report.");
        }
    }

    private ChatResponse provideFixSuggestions (String userId, String message, UserSession session){
        if(!session.hasActiveReport()){
            return ChatResponse.prompt("First analyze a website to get a specific fixes");
        }
        try{
            String fixes = geminiService.suggestFixes(
                    session.getCurrentReport(),
                    message,
                    session.getChatHistory()
            );

            session.addMessage(new ChatMessage(userId, message, fixes, LocalDateTime.now()));

            return ChatResponse.forFixSuggestions(
                    "\uD83D\uDEE0\uFE0F Recommended Fixes:\\n" + fixes,
                    session.getCurrentReport()
            );
        }catch (Exception e){
            log.error("Failed to generate fixes", e);
            return ChatResponse.error("Couldn't generate fixes. Try asking differently.");
        }
    }

    private ChatResponse handleBrokenLinkQuery (String userId, String message, UserSession session){

        if(!session.hasActiveReport()){
            return ChatResponse.prompt("Analyze a website first to check broken links.");
        }

        try{
            // Get required data
            int count = session.getBrokenLinksCount();
            List<String> brokenLinks = brokenLinkAndDuplicateTracker.getBrokenLinksDetails(session.getCurrentAnalysis());

            String analysis = geminiService.analyzeBrokenLinks(
                    session.getCurrentReport(),
                    count,
                    brokenLinks,
                    message,
                    session.getChatHistory()
            );

            session.addMessage(new ChatMessage(userId, message, analysis, LocalDateTime.now()));

            return ChatResponse.forReportQuestion(
                    "\uD83D\uDD17 Broken Link Analysis:\\n" + analysis,
                    session.getCurrentReport()
            );
        }catch (Exception e){
            log.error("Broken link analysis failed", e);
            return ChatResponse.error("Failed to analyze broken links.");
        }
    }

    private ChatResponse provideTechnicalAdvice (String message, UserSession session){
        try{
            String advice = geminiService.provideTechAdvice(
                    message,
                    session.hasActiveReport() ? session.getCurrentReport() : null,
                    session.getChatHistory()
            );

            session.addMessage(new ChatMessage(session.getUserId(), message, advice, LocalDateTime.now()));
            return ChatResponse.forTechAdvice(advice);
        }catch (Exception e){
            log.error("Tech advice failed", e);
            return ChatResponse.error("Couldn't generate technical advice.");
        }
    }

    private ChatResponse provideGeneralSeoAdvice(String message, UserSession session){
        try {
            String advice = geminiService.answerGeneralSeoQuestion(
                    message,
                    session.getChatHistory()
            );
            session.addMessage(new ChatMessage(session.getUserId(), message, advice, LocalDateTime.now()));
            return ChatResponse.forGeneral(advice);
        }catch (Exception e){
            log.error("SEO advice failed", e);
            return ChatResponse.error("Couldn't provide SEO advice.");
        }
    }

    private ChatResponse handleUnsupportedQuery(){
        return ChatResponse.prompt(
                "I specialize in:\n" +
                        "- Website analysis ('analyze example.com')\n" +
                        "- SEO optimization\n" +
                        "- Technical improvements\n" +
                        "- Fixing website issues\n\n" +
                        "What would you like help with?"
        );
    }

    private String extractUrl(String message) {
        // Implement your URL extraction logic
        return message.replaceAll(".*(https?://\\S+).*", "$1");
    }
}