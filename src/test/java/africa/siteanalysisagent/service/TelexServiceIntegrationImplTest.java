//package africa.siteanalysisagent.service;
//
//import africa.siteanalysisagent.dto.TelexUserRequest;
//import africa.siteanalysisagent.model.Data;
//import africa.siteanalysisagent.model.Descriptions;
//import africa.siteanalysisagent.model.Setting;
//import africa.siteanalysisagent.model.TelexIntegration;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.List;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@ExtendWith(MockitoExtension.class)
//class TelexServiceIntegrationImplTest {
//
//    @Mock
//    private MetaAnalysisService metaAnalysisService;
//
//    @InjectMocks
//    private TelexServiceIntegrationImpl telexServiceIntegration;
//
//    private TelexIntegration createValidTelexIntegration() {
//        List<Setting> settings = List.of(
//                new Setting("webhook_url", "text", "provide your telex channel webhook url", true, "https://webhook.url")
//        );
//        Data.DateInfo dateInfo = new Data.DateInfo("2025-03-12", "2025-03-12");
//        Descriptions descriptions = new Descriptions(
//                "Site Analysis Agent",
//                "Site Analysis agent for Telex Integration: A tool that helps you analyze your website's SEO and meta tags.",
//                "https://example.com/logo.png",
//                "https://site-analysis-agent.onrender.com/",
//                "#fff"
//        );
//        Data data = new Data(
//                dateInfo,
//                descriptions,
//                true,
//                "modifier",
//                "CRM & Customer Support",
//                List.of("Single page meta analysis", "Internal link crawling", "Broken link detection", "AI-powered meta suggestions"),
//                "Telin",
//                settings,
//                "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/scrape",
//                "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/scrape"
//        );
//        return new TelexIntegration(data);
//    }
//
//    @Test
//    public void testGetTelexConfigThrowsExceptionDueToMissingWebhookUrl() {
//        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
//            telexServiceIntegration.getTelexConfig();
//        });
//        assertEquals("Webhook URL is missing from Telex settings", exception.getMessage());
//    }
//
//    @Test
//    public void testScrapeAndGenerateUrlReportWithInvalidUrl() {
//        // Provide an input that will not pass the URL regex (after sanitization).
//        TelexUserRequest request = new TelexUserRequest( "invalid-url", List.of());
//        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
//            telexServiceIntegration.scrapeAndGenerateUrlReport(request);
//        });
//        assertEquals("Invalid URL", exception.getMessage());
//    }
//
//    @Test
//    public void testScrapeAndGenerateUrlReportWithNullDocument() throws Exception {
//        TelexServiceIntegrationImpl spyService = Mockito.spy(telexServiceIntegration);
//        Mockito.doReturn(createValidTelexIntegration()).when(spyService).getTelexConfig();
//
//        TelexUserRequest request = new TelexUserRequest("https://example.com", List.of());
//        Mockito.when(metaAnalysisService.scrape("https://example.com")).thenReturn(null);
//
//        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
//            spyService.scrapeAndGenerateUrlReport(request);
//        });
//        assertEquals("Invalid URL", exception.getMessage());
//    }
//
//    @Test
//    public void testScrapeAndGenerateUrlReportSuccess() throws Exception {
//        TelexServiceIntegrationImpl spyService = Mockito.spy(telexServiceIntegration);
//        Mockito.doReturn(createValidTelexIntegration()).when(spyService).getTelexConfig();
//
//        TelexUserRequest request = new TelexUserRequest( "https://example.com", List.of());
//
//        Document dummyDocument = Jsoup.parse("<html><head></head><body></body></html>");
//        Mockito.when(metaAnalysisService.scrape("https://example.com")).thenReturn(dummyDocument);
//        Mockito.when(metaAnalysisService.generateSeoReport("https://example.com", createValidTelexIntegration().data().settings()))
//                .thenReturn("SEO Report");
//        Mockito.when(metaAnalysisService.checkMetaTags(dummyDocument))
//                .thenReturn(List.of("Issue1", "Issue2"));
//
//        Map<String, Object> result = spyService.scrapeAndGenerateUrlReport(request);
//
//        assertNotNull(result);
//        assertEquals("https://example.com", result.get("url"));
//        assertEquals("SEO Report", result.get("seoReport"));
//        assertEquals(List.of("Issue1", "Issue2"), result.get("metaTagIssues"));
//    }
//}