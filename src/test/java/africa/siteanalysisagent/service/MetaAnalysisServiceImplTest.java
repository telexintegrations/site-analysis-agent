//package africa.siteanalysisagent.service;
//
//import africa.siteanalysisagent.dto.TelexUserRequest;
//import africa.siteanalysisagent.dto.Setting;
//import org.apache.commons.lang3.StringUtils;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.NullSource;
//import org.junit.jupiter.params.provider.ValueSource;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class MetaAnalysisServiceImplTest {
//
//    @Mock
//    private TelexServiceImpl telexService;
//
//    @InjectMocks
//    private MetaAnalysisServiceImpl metaAnalysisService;
//
//
//    @ParameterizedTest
//    @NullSource
//    @ValueSource(strings = {StringUtils.EMPTY, StringUtils.SPACE})
//    void testIsSingleUrl_Valid(String inValidUrl) {
//        String validUrl = "https://example.com/page";
//        assertTrue(metaAnalysisService.isSingleUrl(validUrl));
//    }
//
//    @Test
//    void testScrape_InvalidUrlThrowsException() {
//        String invalidUrl = "invalid-url";
//        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
//                () -> metaAnalysisService.scrape(invalidUrl));
//        assertEquals("Invalid URL. please input a single page URL", ex.getMessage());
//    }
//
//    @Test
//    void testCheckMetaTags_AllPresent() {
//        String html = "<html><head>" +
//                "<title>Test Title</title>" +
//                "<meta name='description' content='Test Description'>" +
//                "<meta name='keyword' content='Test Keyword'>" +
//                "</head><body></body></html>";
//        Document document = Jsoup.parse(html);
//        List<String> issues = metaAnalysisService.checkMetaTags(document);
//
//        assertTrue(issues.contains("Title tag: Test Title\n"));
//        assertTrue(issues.contains("Meta Description: Test Description\n"));
//        assertTrue(issues.contains("Meta Keywords: Test Keyword\n"));
//    }
//
//    @Test
//    void testCheckMetaTags_MissingTags() {
//        String html = "<html><head></head><body></body></html>";
//        Document document = Jsoup.parse(html);
//        List<String> issues = metaAnalysisService.checkMetaTags(document);
//
//        assertTrue(issues.contains("Missing title tag\n"));
//        assertTrue(issues.contains("Missing meta description\n"));
//        assertTrue(issues.contains("Missing meta keywords\n"));
//    }
//
//    @Test
//    void testGenerateSeoReport_Success() throws Exception {
//        String validUrl = "https://example.com/page";
//        String webhookUrl = "http://webhook.com";
//        TelexUserRequest request = new TelexUserRequest(validUrl, List.of(
//                new Setting("webhook_url", "text", "provide your telex channel webhook url", webhookUrl, true)
//        ));
//
//        MetaAnalysisServiceImpl spyService = Mockito.spy(metaAnalysisService);
//
//        String html = "<html><head>" +
//                "<title>Test Title</title>" +
//                "<meta name='description' content='Test Description'>" +
//                "<meta name='keyword' content='Test Keyword'>" +
//                "</head><body></body></html>";
//        Document dummyDocument = Jsoup.parse(html);
//        doReturn(dummyDocument).when(spyService).scrape(validUrl);
//
//        String report = spyService.generateSeoReport(validUrl, webhookUrl, String.valueOf(request));
//
//        verify(telexService, times(1)).notifyTelex(report, webhookUrl);
//
//        assertTrue(report.startsWith("SEO Analysis Report for: " + validUrl));
//        assertTrue(report.contains("Title tag: Test Title"));
//        assertTrue(report.contains("Meta Description: Test Description"));
//        assertTrue(report.contains("Meta Keywords: Test Keyword"));
//    }
//}