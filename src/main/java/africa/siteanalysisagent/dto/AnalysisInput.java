package africa.siteanalysisagent.dto;

import java.util.List;

// For Gemini integration
public record AnalysisInput(
        String baseUrl,
        List<String> scannedPages,
        LinkAnalysis linkAnalysis
) {}
