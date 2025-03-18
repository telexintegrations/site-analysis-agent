package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Setting;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntegration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelexServiceIntegrationImplTest {

    @Mock
    private MetaAnalysisService metaAnalysisService;

    @InjectMocks
    private TelexServiceIntegrationImpl telexServiceIntegration;


    @Test
    void testGetTelexConfig() throws Exception {
        TelexIntegration telexIntegration = telexServiceIntegration.getTelexConfig();
        assertNotNull(telexIntegration);
        assertTrue(telexIntegration.data().is_active());
        List<?> settings = telexIntegration.data().settings();
        assertNotNull(settings);
        assertFalse(settings.isEmpty());
        Setting setting = (Setting) settings.get(0);
        assertEquals("webhook_url", setting.label());
        assertEquals("", setting.defaultValue());
    }

    @Test
    void testScrapeAndGenerateUrlReport_Success() throws Exception {
        String validUrl = "https://example.com/page";
        Setting validSetting = new Setting("webhook_url", "text", "provide your telex channel webhook url", "http://webhook.com", true);
        TelexUserRequest request = new TelexUserRequest(validUrl, List.of(validSetting));

        String dummyHtml = "<html><head><title>Test</title><meta name='description' content='desc'></head><body></body></html>";
        Document dummyDocument = Jsoup.parse(dummyHtml);
        when(metaAnalysisService.scrape(validUrl)).thenReturn(dummyDocument);

        String dummySeoReport = "Dummy SEO Report";
        when(metaAnalysisService.generateSeoReport(validUrl, "http://webhook.com")).thenReturn(dummySeoReport);

        List<String> dummyMetaIssues = List.of("Issue1", "Issue2");
        when(metaAnalysisService.checkMetaTags(dummyDocument)).thenReturn(dummyMetaIssues);

        Map<String, Object> result = telexServiceIntegration.scrapeAndGenerateUrlReport(request);

        assertNotNull(result);
        assertEquals(validUrl, result.get("url"));
        assertEquals(dummySeoReport, result.get("seoReport"));
        assertEquals(dummyMetaIssues, result.get("metaTagIssues"));
    }

    @Test
    void testScrapeAndGenerateUrlReport_InvalidUrl() {
        String invalidUrl = "invalid-url";
        Setting validSetting = new Setting("webhook_url", "text", "provide your telex channel webhook url", "http://webhook.com", true);
        TelexUserRequest request = new TelexUserRequest(invalidUrl, List.of(validSetting));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> telexServiceIntegration.scrapeAndGenerateUrlReport(request));
        assertEquals("Invalid URL", ex.getMessage());
    }

    @Test
    void testScrapeAndGenerateUrlReport_MissingWebhook() {
        String validUrl = "https://example.com/page";
        Setting missingWebhookSetting = new Setting("webhook_url", "text", "provide your telex channel webhook url", " ", true);
        TelexUserRequest request = new TelexUserRequest(validUrl, List.of(missingWebhookSetting));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> telexServiceIntegration.scrapeAndGenerateUrlReport(request));
        assertEquals("Webhook URL is missing from Telex settings", ex.getMessage());
    }

    @Test
    void testScrapeAndGenerateUrlReport_NullDocument() throws Exception {
        String validUrl = "https://example.com/page";
        Setting validSetting = new Setting("webhook_url", "text", "provide your telex channel webhook url", "http://webhook.com", true);
        TelexUserRequest request = new TelexUserRequest(validUrl, List.of(validSetting));

        when(metaAnalysisService.scrape(validUrl)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> telexServiceIntegration.scrapeAndGenerateUrlReport(request));
        assertEquals("Invalid URL", ex.getMessage());
    }
}