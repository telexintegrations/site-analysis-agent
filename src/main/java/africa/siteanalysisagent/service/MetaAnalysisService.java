package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.SiteAnalysis;
import africa.siteanalysisagent.model.SEOReport;

import java.io.IOException;

public interface MetaAnalysisService {
    public SiteAnalysis analyzeSite(String channelId, String baseUrl) throws IOException;

}
