package africa.siteanalysisagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserIntent {

    public enum IntentType{
        NEW_ANALYSIS,       // "analyze example.com"
        REPORT_QUESTION,    // "Why is my score low?"
        FIX_SUGGESTION,     // "How to fix my meta tags?"
        TECH_ADVICE,        // "Best framework for SEO?"
        GENERAL_SEO,        // "What are backlinks?"
        BROKEN_LINK_HELP,   // "Why are my links broken?"
        UNSUPPORTED         // Off-topic questions
    }

    private IntentType type;
    private String url;

}
