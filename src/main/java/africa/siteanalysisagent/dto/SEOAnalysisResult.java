package africa.siteanalysisagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the comprehensive results of an SEO analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SEOAnalysisResult {
    private int score;
    private String strengths;
    private String issues;
    private String recommendations;
    private String optimizedTags;
    private String technicalDetails;
    private String quickFixes;

    /**
     * Creates an error result with the given message
     */
    public static SEOAnalysisResult error(String message) {
        return new SEOAnalysisResult(
                0,
                "",
                message,
                "",
                "",
                "",
                ""
        );
    }

    /**
     * Creates a successful result with all analysis data
     */
    public static SEOAnalysisResult success(
            int score,
            String strengths,
            String issues,
            String recommendations,
            String optimizedTags,
            String technicalDetails,
            String quickFixes
    ) {
        return new SEOAnalysisResult(
                score,
                strengths,
                issues,
                recommendations,
                optimizedTags,
                technicalDetails,
                quickFixes
        );
    }

    /**
     * Returns a markdown-formatted version of the report
     */
    public String toMarkdown() {
        return String.format(
                "# SEO Analysis Report\n\n" +
                        "## Overall Score: %d/100\n\n" +
                        "### 🟢 Strengths\n%s\n\n" +
                        "### 🔴 Issues\n%s\n\n" +
                        "### 💡 Recommendations\n%s\n\n" +
                        "### 🏷️ Optimized Tags\n```\n%s\n```\n\n" +
                        "### ⚙️ Technical Details\n%s\n\n" +
                        "### 🚀 Quick Fixes\n%s",
                score,
                strengths,
                issues,
                recommendations,
                optimizedTags,
                technicalDetails,
                quickFixes
        );
    }

    /**
     * Returns a shortened version for previews
     */
    public String toSummary() {
        return String.format(
                "Score: %d/100 | Issues: %d | Recommendations: %d",
                score,
                issues.split("\n").length,
                recommendations.split("\n").length
        );
    }
}