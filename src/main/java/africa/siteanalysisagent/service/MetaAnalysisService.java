package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.SiteAnalysis;
import africa.siteanalysisagent.model.SEOReport;

import java.io.IOException;

public interface MetaAnalysisService {
    public SiteAnalysis analyzeSite(String baseUrl) throws IOException;

    SEOReport generateFullReport(String url) throws IOException;
}
