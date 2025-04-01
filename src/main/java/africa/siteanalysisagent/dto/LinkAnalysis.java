package africa.siteanalysisagent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
public record LinkAnalysis(
        Map<String, String> activeLinks,
        Map<String, String> brokenLinks
) {}
