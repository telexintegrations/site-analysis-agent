package africa.siteanalysisagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents the comprehensive results of an SEO analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SEOAnalysisResult {
    private String url;
    private String strengths;
    private String issues;
    private String recommendations;
    private String optimizedTags;
    private String technicalDetails;
    private String quickFixes;

    public static SEOAnalysisResult error(String message) {
        return SEOAnalysisResult.builder()
                .issues(message)
                .build();
    }

    public static SEOAnalysisResult success(String url, String strengths,
                                            String issues, String recommendations,
                                            String optimizedTags, String technicalDetails,
                                            String quickFixes) {
        return new SEOAnalysisResult(url,strengths, issues,
                recommendations, optimizedTags,
                technicalDetails, quickFixes);
    }


    /**
     * Returns a markdown-formatted version of the report
     */
    public String toMarkdown() {
        return String.format(
                "# SEO Analysis Report\n\n" +
                        "## URL: %s\n" +
                        "### 🟢 Strengths\n%s\n\n" +
                        "### 🔴 Issues\n%s\n\n" +
                        "### 💡 Recommendations\n%s\n\n" +
                        "### 🏷️ Optimized Tags\n```\n%s\n```\n\n" +
                        "### ⚙️ Technical Details\n%s\n\n" +
                        "### 🚀 Quick Fixes\n%s",
                url,strengths, issues,
                recommendations, optimizedTags,
                technicalDetails, quickFixes
        );
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "url", url,
                "strengths", strengths,
                "issues", issues,
                "recommendations", recommendations,
                "optimizedTags", optimizedTags,
                "technicalDetails", technicalDetails,
                "quickFixes", quickFixes
        );
    }


    public String toSummary() {
        return String.format(
                "Score: %d/100 | Issues: %d | Recommendations: %d",
                issues == null ? 0 : issues.split("\n").length,
                recommendations == null ? 0 : recommendations.split("\n").length
        );
    }
}