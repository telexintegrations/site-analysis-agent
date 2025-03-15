package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Setting;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class TelexServiceIntegrationImplTest {

    @Mock
    private MetaAnalysisService metaAnalysisService;

    @InjectMocks
    private TelexServiceIntegrationImpl telexServiceIntegration;

    @BeforeEach
    void setUp() {
        telexServiceIntegration = new TelexServiceIntegrationImpl(metaAnalysisService);
    }

    @Test
    void testGetTelexConfig_Success() throws JsonProcessingException {
        TelexIntegration telexIntegration = telexServiceIntegration.getTelexConfig();

        assertNotNull(telexIntegration);
        assertNotNull(telexIntegration.data());
        assertEquals("Site Analysis Agent", telexIntegration.data().descriptions().app_name());
    }

    @Test
    void testScrapeAndGenerateUrlReport_Success() throws IOException {
        String validUrl = "https://example.com";
        List<Setting> mockSettings = List.of();
        TelexUserRequest request = new TelexUserRequest(validUrl, mockSettings);

        Document mockDocument = new Document(validUrl);
        String mockSeoReport = "SEO Report";
        List<String> mockMetaTagIssues = List.of("Missing description tag");

        when(metaAnalysisService.scrape(validUrl)).thenReturn(mockDocument);
        when(metaAnalysisService.generateSeoReport(validUrl)).thenReturn(mockSeoReport);
        when(metaAnalysisService.checkMetaTags(mockDocument)).thenReturn(mockMetaTagIssues);

        Map<String, Object> result = telexServiceIntegration.scrapeAndGenerateUrlReport(request);

        assertNotNull(result);
        assertEquals(validUrl, result.get("url"));
        assertEquals(mockSeoReport, result.get("seoReport"));
        assertEquals(mockMetaTagIssues, result.get("metaTagIssues"));
    }


}