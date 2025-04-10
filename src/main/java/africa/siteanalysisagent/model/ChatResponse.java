package africa.siteanalysisagent.model;

import africa.siteanalysisagent.dto.Button;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatResponse {
    private ResponseType type;
    private String message;
    private SEOReport report;  // Optional, only for report-related responses
    private Map<String, Object> metadata;  // Additional context if needed
    private LocalDateTime timestamp;
    private List<Button> buttons;


    // Private constructor
    private ChatResponse(ResponseType type, String message, SEOReport report,
                         Map<String, Object> metadata, LocalDateTime timestamp) {
        this.type = type;
        this.message = message;
        this.report = report;
        this.metadata = metadata;
        this.timestamp = timestamp;
    }

    public ChatResponse(ResponseType type, String message, SEOReport report,
                        Map<String, Object> data, LocalDateTime timestamp,
                        List<Button> buttons) {
        this(type, message, report, data, timestamp);
        this.buttons = buttons;
    }

    // Response categories
    public enum ResponseType {
        ANALYSIS_RESULT,     // When sharing a new site analysis
        REPORT_INSIGHT,     // Answering questions about a report
        FIX_RECOMMENDATION, // Suggesting fixes for issues
        TECH_GUIDANCE,      // Advice on tech stack improvements
        SEO_ADVICE,         // General SEO best practices
        ERROR,              // Error messages
        PROMPT,              // Requests for more info (e.g., "Which URL?")
        INFO,
        SUCCESS
    }


    public ChatResponse withButtons(List<Button> buttons) {
        this.buttons = buttons;
        return this;
    }
    
    // For general SEO advice (uses SEO_ADVICE type)
    public static ChatResponse forGeneral(String advice) {
        return new ChatResponse(
                ResponseType.SEO_ADVICE,
                advice,
                null,
                null,
                LocalDateTime.now()
        );
    }

    // Other factory methods to match your ResponseType enum:
    public static ChatResponse forAnalysis(SEOReport report, String summary) {
        return new ChatResponse(
                ResponseType.ANALYSIS_RESULT,
                summary,
                report,
                null,
                LocalDateTime.now()
        );
    }

    public static ChatResponse forReportQuestion(String answer, SEOReport report) {
        return new ChatResponse(
                ResponseType.REPORT_INSIGHT,
                answer,
                report,
                null,
                LocalDateTime.now()
        );
    }

    public static ChatResponse forFixSuggestions(String fixes, SEOReport currentReport) {
        return new ChatResponse(
                ResponseType.FIX_RECOMMENDATION,
                fixes,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static ChatResponse forTechAdvice(String advice) {
        return new ChatResponse(
                ResponseType.TECH_GUIDANCE,
                advice,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static ChatResponse error(String errorMessage) {
        return new ChatResponse(
                ResponseType.ERROR,
                errorMessage,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static ChatResponse prompt(String promptMessage) {
        return new ChatResponse(
                ResponseType.PROMPT,
                promptMessage,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static ChatResponse info(String infoMssage){
        return new ChatResponse(
                ResponseType.INFO,
                infoMssage,
                null,
                null,
                LocalDateTime.now()
        );
    }

    public static ChatResponse success(String message){
        return new ChatResponse(
                ResponseType.SUCCESS,
                message,
                null,
                null,
                LocalDateTime.now()
        );
    }


}