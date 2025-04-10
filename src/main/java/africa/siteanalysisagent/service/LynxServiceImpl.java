package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.SiteAnalysis;
import africa.siteanalysisagent.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class LynxServiceImpl implements LynxService {

    private final MetaAnalysisService metaAnalysisService;
    private final GeminiService geminiService;
    private final TelexService telexService; // Already configured with channels
    private final BrokenLinkAndDuplicateTracker brokenLinkAndDuplicateTracker;
    private final ScheduleService scheduleService;

    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();


    @Override
    public ChatResponse handleAnalysisRequest(String userId, String url) {
        UserSession session = userSessions.get(userId);
        try {
            // Forward to MetaAnalysisService with Telex channel
            metaAnalysisService.analyzeSite(session.getChannelId(),url);

            return ChatResponse.success("Scan initiated for " + url)
                    .withButtons(List.of(
                            new Button("📊 Report", "report", "get_report:" + url),
                            new Button("📅 Schedule", "schedule", "schedule_scan:" + url)
                    ));
        } catch (Exception e) {
            return ChatResponse.error("Failed to start scan: " + e.getMessage());
        }
    }

    @Override
    public ChatResponse handleScheduleRequest(String userId, String url, String interval) {
        // Handle scheduling through ScheduleService
        return scheduleService.createSchedule(userId, url, Long.valueOf(interval));
    }

@Override
    public ChatResponse processMessage(ChatMessage chatMessage) {
        String userId = chatMessage.getUserId();
        String userMessage = chatMessage.getUserMessage();

        UserSession session = userSessions.computeIfAbsent(userId, k -> new UserSession(userId));
        session.updateLastActivity();

        try {
            // Handle button actions
            if (userMessage.startsWith("button:")) {
                return handleButtonAction(userId, userMessage, session);
            }

            // Check if we're expecting a custom schedule input
            if (session.isAwaitingCustomSchedule()) {
                session.clearAwaitingCustomSchedule();

                // Try to parse custom interval
                ChatResponse response = scheduleService.handleCustomScheduleRequest(
                        userId,
                        userMessage,
                        session.getCurrentUrl()
                );

                // If parsing failed, show options again
                if (response.getType() == ChatResponse.ResponseType.ERROR) {
                    return ChatResponse.prompt(response.getMessage())
                            .withButtons(List.of(
                                    new Button("Try Again", "retry", "schedule_custom:" + session.getCurrentUrl()),
                                    new Button("Cancel", "cancel", "schedule_cancel")
                            ));
                }
                return response;
            }

            // Analyze intent
            UserIntent intent = analyzeIntent(userMessage, session);

            // Process based on intent
            ChatResponse response = switch (intent.getType()) {
                case NEW_ANALYSIS -> processAnalysisRequest(userId, userMessage, intent.getUrl(), session);
                case REPORT_QUESTION -> answerReportQuestion(userId, userMessage, session);
                case FIX_SUGGESTION -> provideFixSuggestions(userId, userMessage, session);
                case TECH_ADVICE -> provideTechnicalAdvice(userMessage, session);
                case GENERAL_SEO -> provideGeneralSeoAdvice(userMessage, session);
                case BROKEN_LINK_HELP -> handleBrokenLinkQuery(userId, userMessage, session);
                case SCHEDULE_SCAN -> handleScheduleRequest(userId, userMessage, intent.getUrl(), session);
                case LIST_SCHEDULES -> listScheduledScans(userId);
                case CANCEL_SCHEDULE -> cancelScheduledScan(userId, userMessage);
                default -> handleUnsupportedQuery();
            };

            // Update session with bot response
            session.addMessage(new ChatMessage(
                    userId,
                    userMessage,
                    response.getMessage(),
                    LocalDateTime.now()
            ));

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
                case SCHEDULE_SCAN -> handleScheduleRequest(userId, userMessage, intent.getUrl(), session);
                case LIST_SCHEDULES -> listScheduledScans(userId);
                case CANCEL_SCHEDULE -> cancelScheduledScan(userId, userMessage);
                default -> handleUnsupportedQuery();
            };

            return response;
        } catch (Exception e) {
            log.error("Chat processing failed for user {}", userId, e);
            return ChatResponse.error("I encountered an error. Please try again.");
        }
    }


    @Override
    public ChatResponse processAnalysisRequest(String userId, String message, String url) {
        UserSession session = userSessions.computeIfAbsent(userId, k -> new UserSession(userId));
        return processAnalysisRequest(userId, message, url, session);
    }

    @Override
    public ChatResponse processAnalysisRequest(String userId, String message, String url, UserSession session) {
        if (url == null || url.isBlank()) {
            return ChatResponse.error("Please provide a valid URL to analyze");

        }

        if (session.getChannelId() == null) {
            session.setChannelId("user_" + userId); // or your default channel logic
        }
        try {

            // Ensure URL has proper scheme
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }


            SiteAnalysis analysis = metaAnalysisService.analyzeSite(session.getChannelId(),url);

            session.setCurrentAnalysis(analysis);
            session.setCurrentUrl(url);
            session.addMessage(new ChatMessage(userId, message, null, LocalDateTime.now()));


// 4. Return response
            return ChatResponse.success("Analysis completed for " + url)
                    .withButtons(List.of(
                            new Button("View Report", "report", "show_report:" + url),
                            new Button("Schedule", "schedule", "schedule_options:" + url)
                    ));
        } catch (IOException e) {
            log.error("Analysis failed for URL: {}", url, e);
            return ChatResponse.error("Failed to analyze " + url + ". Please check the URL and try again.");
        }
    }









        private void sendSeoHighlights(String channelId, SEOReport report) {
        String highlights = String.format("""
        📊 *SEO Overview*
        ━━━━━━━━━━━━━━━
        • Score: %d/100
        • Critical Issue: %s
        • Quick Win: %s
        ━━━━━━━━━━━━━━━
        """,
                report.getScore(),
                report.getIssues(),
                report.getQuickFixes()
        );
        telexService.sendMessage(channelId, highlights);
    }


    @Override
    public ChatResponse handleScheduleRequest(String userId, String message, String url, UserSession session) {
        // 1. Get URL from session if not provided
        if (url == null && session.hasActiveReport()) {
            url = session.getCurrentUrl();
        }

        // 2. If still no URL, prompt for it
        if (url == null) {
            return ChatResponse.prompt("Please provide a URL to schedule scans for:")
                    .withButtons(List.of(new Button("Cancel", "cancel", "schedule_cancel")));
        }

        // 3. Check if message contains a custom interval pattern
        ScheduleService.ScheduleRequest customRequest = scheduleService.parseCustomInterval(message);
        if (customRequest != null) {
            // Handle custom interval directly
            boolean scheduled = scheduleService.scheduleScan(
                    userId,
                    url,
                    customRequest.value(),
                    customRequest.unit()
            );

            if (scheduled) {
                String unitStr = customRequest.value() == 1 ?
                        customRequest.unit().toString().toLowerCase().replace("s", "") :
                        customRequest.unit().toString().toLowerCase();

                return ChatResponse.success(
                        String.format("✅ Scheduled scan for %s every %d %s",
                                url, customRequest.value(), unitStr)
                );
            }
            return ChatResponse.error("Invalid interval. Minimum: 30 seconds, Maximum: 30 days");
        }

        // 4. Check for default hour intervals (original functionality)
        Integer hours = parseDefaultHours(message);
        if (hours != null) {
            boolean scheduled = scheduleService.scheduleDefaultInterval(userId, url, Long.valueOf(hours));
            return scheduled ?
                    ChatResponse.success(String.format("✅ Scheduled scan for %s every %d hours", url, hours)) :
                    ChatResponse.error("Invalid interval. Use 1, 3, 6, 12, or 24 hours");
        }

        // 5. If no interval specified, show options
        return showScheduleOptions(url);
    }

    private Integer parseDefaultHours(String message) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*hours?");
        Matcher matcher = pattern.matcher(message.toLowerCase());
        if (matcher.find()) {
            int hours = Integer.parseInt(matcher.group(1));
            return Set.of(1, 3, 6, 12, 24).contains(hours) ? hours : null;
        }
        return null;
    }

    private ChatResponse showScheduleOptions(String url) {
        List<Button> buttons = new ArrayList<>();

        // Add default interval buttons
        for (int hours : List.of(1, 3, 6, 12, 24)) {
            buttons.add(new Button(
                    hours + "h",
                    hours + " hours",
                    "schedule:" + url + ":" + hours + ":hours"
            ));
        }

        // Add custom option
        buttons.add(new Button(
                "Custom",
                "custom",
                "schedule_custom:" + url
        ));

        return ChatResponse.prompt("How often should we scan " + url + "?")
                .withButtons(buttons);
    }

    @Override
    public ChatResponse listScheduledScans(String userId) {
        return scheduleService.listSchedules(userId);
    }

    @Override
    public ChatResponse cancelScheduledScan(String userId, String message) {
        return scheduleService.cancelSchedule(userId, message);
    }


    private ChatResponse handleButtonAction(String userId, String buttonMessage, UserSession session) {
        String[] parts = buttonMessage.split(":");
        String action = parts[1];
        String url = parts.length > 2 ? parts[2] : session.getCurrentUrl();

        switch (action) {
            case "show_schedule_options":
                return showScheduleOptions(url);

            case "schedule_hours":
                int hours = Integer.parseInt(parts[3]);
                boolean scheduled = scheduleService.scheduleDefaultInterval(userId, url, (long) hours);
                return scheduled ?
                        ChatResponse.success("✅ Scheduled scan for " + url + " every " + hours + " hours") :
                        ChatResponse.error("Failed to schedule scan");

            case "schedule_custom":
                session.setCurrentUrl(url); // Ensure URL is set for the follow-up
                session.setAwaitingCustomSchedule(true);
                return ChatResponse.prompt("""
                ⌛ *Custom Schedule*
                ━━━━━━━━━━━━━━━
                Enter your desired interval:
                Examples:
                • "30 minutes"
                • "2 hours"
                • "1 day"
                Minimum: 30 seconds
                Maximum: 30 days
                ━━━━━━━━━━━━━━━
                """)
                        .withButtons(List.of(
                                new Button("Cancel", "cancel", "schedule_cancel:" + url)
                        ));

            case "retry": // For retrying custom schedule input
                session.setAwaitingCustomSchedule(true);
                return ChatResponse.prompt("Please enter your custom interval again:")
                        .withButtons(List.of(
                                new Button("Cancel", "cancel", "schedule_cancel:" + url)
                        ));


            // Handle custom input from user message
            case "process_custom_schedule":
                String intervalInput = parts[3];
                return scheduleService.handleCustomScheduleRequest(userId, intervalInput, url);

            case "list_schedules":
                return scheduleService.listSchedules(userId);

            case "schedule_cancel_all":
                return scheduleService.cancelSchedule(userId, "cancel all");

            case "schedule_cancel":
                return scheduleService.cancelSchedule(userId, "cancel " + url);
            case "show_seo_report":
                return showSeoReport(userId, session);
            case "show_fixes":
                return showFixSuggestions(userId, session);

            default:
                return ChatResponse.error("Unknown button action");
        }
    }

    private ChatResponse showSeoReport(String userId, UserSession session) {
        if (!session.hasActiveReport()) {
            return ChatResponse.error("No report available. Please scan a website first.");
        }

        String reportSummary = geminiService.generateReportSummary(session.getCurrentReport());

        List<Button> reportButtons = Arrays.asList(
                new Button("🔄 Scan Again", "rescan", "new_scan:" + session.getCurrentUrl()),
                new Button("📅 Schedule", "schedule", "show_schedule_options:" + session.getCurrentUrl())
        );

        return ChatResponse.info("📊 SEO Report:\n" + reportSummary)
                .withButtons(reportButtons);
    }

    private ChatResponse showFixSuggestions(String userId, UserSession session) {
        if (!session.hasActiveReport()) {
            return ChatResponse.error("No issues to fix. Please scan a website first.");
        }

        try {
            String fixes = geminiService.suggestFixes(
                    session.getCurrentReport(),
                    "Provide fixes for all issues",
                    session.getChatHistory()
            );

            List<Button> fixButtons = Arrays.asList(
                    new Button("✅ Mark as Fixed", "fixed", "mark_fixed"),
                    new Button("🔄 Scan Again", "rescan", "new_scan:" + session.getCurrentUrl())
            );

            return ChatResponse.info("🔧 Recommended Fixes:\n" + fixes)
                    .withButtons(fixButtons);
        } catch (Exception e) {
            log.error("Failed to generate fixes", e);
            return ChatResponse.error("Failed to generate fixes. Please try again.");
        }
    }



    private ChatResponse createPostScanResponse(String url) {
        // Main response message
        String message = String.format("""
        ✅ *Scan Completed for %s*
        ━━━━━━━━━━━━━━━
        What would you like to do next?
        """, url);

        // Create action buttons
        List<Button> actionButtons = Arrays.asList(
                new Button("📅 Schedule Scan", "schedule", "show_schedule_options:" + url),
                new Button("📊 View SEO Report", "view_report", "show_seo_report:" + url),
                new Button("🔧 Fix Issues", "fix_issues", "show_fixes:" + url)
        );

        // Add quick response hints
        String quickReplies = """
        ━━━━━━━━━━━━━━━
        Quick reply with:
        • "schedule" - Set up automatic scans
        • "report" - View full analysis
        • "fix" - See recommended fixes
        """;

        return ChatResponse.info(message + quickReplies)
                .withButtons(actionButtons);
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

    private UserIntent analyzeIntent(String message, UserSession session) {
        String lowerMsg = message.toLowerCase().trim();

        if (lowerMsg.matches(".*(every|each|\\d+\\s*(second|minute|hour|day)).*") &&
                lowerMsg.matches(".*(schedule|scan|check|monitor).*")) {

            // Try to extract both URL and interval
            String url = extractUrl(message);
            String request = String.valueOf(scheduleService.parseCustomInterval(message));

            if (request != null && url != null) {
                return new UserIntent(
                        UserIntent.IntentType.SCHEDULE_SCAN,
                        request // Store parsed interval in intent
                );
            }
            return new UserIntent(UserIntent.IntentType.SCHEDULE_SCAN, url);
        }


        if (lowerMsg.matches(".*(report|analysis|seo|results).*")) {
            return new UserIntent(UserIntent.IntentType.VIEW_REPORT, null);
        }

        // Enhanced scheduling keywords
        if (lowerMsg.matches(".*(schedule|regular|automatic|recurring|periodic|setup|enable).*") &&
                lowerMsg.matches(".*(scan|analysis|check|report|monitor).*")) {
            return new UserIntent(UserIntent.IntentType.SCHEDULE_SCAN, extractUrl(message));
        }

        if (lowerMsg.matches(".*(list|show|view|display|see).*(scheduled|recurring|automatic|periodic).*(scans|analysis|reports).*")) {
            return new UserIntent(UserIntent.IntentType.LIST_SCHEDULES, null);
        }

        if (lowerMsg.matches(".*(cancel|stop|remove|delete|disable).*(scheduled|recurring|automatic|periodic).*(scan|analysis|report).*")) {
            return new UserIntent(UserIntent.IntentType.CANCEL_SCHEDULE, extractUrl(message));
        }

        // Existing intent analysis
        if (lowerMsg.matches(".*(analyze|scan|check|test)\\s+(http|www|\\.[a-z]{2,}).*")) {
            return new UserIntent(UserIntent.IntentType.NEW_ANALYSIS, extractUrl(message));
        }

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

        if (lowerMsg.matches(".*(tech|framework|library|tool|optimize|speed).*")) {
            return new UserIntent(UserIntent.IntentType.TECH_ADVICE, null);
        }

        if (lowerMsg.matches(".*(seo|search engine|ranking|traffic|google).*")) {
            return new UserIntent(UserIntent.IntentType.GENERAL_SEO, null);
        }

        return new UserIntent(UserIntent.IntentType.UNSUPPORTED, null);
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