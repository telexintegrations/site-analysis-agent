//package africa.siteanalysisagent.service;
//
//import africa.siteanalysisagent.model.Setting;
//import org.apache.commons.lang3.StringUtils;
//import org.jsoup.Connection;
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
//import java.io.IOException;
//import java.util.Collections;
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
//    @Test
//    void testIsSingleUrl_ValidSinglePage() {
//        assertTrue(metaAnalysisService.isSingleUrl("https://example.com/page"));
//    }
//
//    @Test
//    void testScrape_ValidUrl() throws IOException {
//        String url = "https://example.com/page";
//
//        Connection mockConnection = Mockito.mock(Connection.class);
//
//        Document mockDocument = Jsoup.parse("<html><head><title>Test</title></head></html>");
//
//        try (var jsoupMock = Mockito.mockStatic(Jsoup.class)) {
//            jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(mockConnection);
//            Mockito.when(mockConnection.timeout(10000)).thenReturn(mockConnection);
//            Mockito.when(mockConnection.get()).thenReturn(mockDocument);
//
//            Document result = metaAnalysisService.scrape(url);
//
//            assertNotNull(result);
//            assertEquals("Test", result.title());
//        }
//    }
//
//    @Test
//    void testCheckMetaTags_AllMetaTagsPresent() {
//        Document document = Jsoup.parse("<html><head><title>Title</title><meta name=\"description\" content=\"Meta description\"/><meta name=\"keyword\" content=\"keywords\"/></head></html>");
//        List<String> issues = metaAnalysisService.checkMetaTags(document);
//        assertEquals(3, issues.size());
//        assertTrue(issues.get(0).contains("Title tag: Title"));
//        assertTrue(issues.get(1).contains("Meta Description: Meta description"));
//        assertTrue(issues.get(2).contains("Meta Keywords: keywords"));
//    }
//
//    @ParameterizedTest
//    @NullSource
//    @ValueSource(strings = {StringUtils.EMPTY, StringUtils.SPACE})
//    public void testIsSingleUrl_Valid(String url) {
//        assertTrue(metaAnalysisService.isSingleUrl("http://example.com/page"));
//    }
//
//    @Test
//    public void testIsSingleUrl_NoSlash() {
//        assertFalse(metaAnalysisService.isSingleUrl("example"));
//    }
//
//    @Test
//    public void testCheckMetaTags_AllTagsPresent() {
//        String html = "<html><head>" +
//                "<title>Test Title</title>" +
//                "<meta name=\"description\" content=\"Test description\">" +
//                "<meta name=\"keyword\" content=\"Test keyword\">" +
//                "</head><body></body></html>";
//        Document document = Jsoup.parse(html);
//        List<String> issues = metaAnalysisService.checkMetaTags(document);
//
//        assertTrue(issues.stream().anyMatch(s -> s.contains("Test Title")));
//        assertTrue(issues.stream().anyMatch(s -> s.contains("Test description")));
//        assertTrue(issues.stream().anyMatch(s -> s.contains("Test keyword")));
//    }
//
//    @Test
//    public void testCheckMetaTags_MissingTags() {
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
//    public void testGenerateSeoReportSuccess() throws Exception {
//        MetaAnalysisServiceImpl spyService = Mockito.spy(metaAnalysisService);
//        String testUrl = "http://example.com/page";
//
//        String html = "<html><head>" +
//                "<title>Test Title</title>" +
//                "<meta name=\"description\" content=\"Test description\">" +
//                "<meta name=\"keyword\" content=\"Test keyword\">" +
//                "</head><body></body></html>";
//        Document dummyDocument = Jsoup.parse(html);
//
//        Mockito.doReturn(dummyDocument).when(spyService).scrape(testUrl);
//
//        List<Setting> settings = Collections.emptyList();
//
//        String report = spyService.generateSeoReport(testUrl, settings);
//
//        verify(telexService, times(1)).notifyTelex(report, settings);
//
//        assertTrue(report.contains("SEO Analysis Report for: " + testUrl));
//        assertTrue(report.contains("Test Title"));
//        assertTrue(report.contains("Test description"));
//        assertTrue(report.contains("Test keyword"));
//    }
//
//    @Test
//    public void testGenerateSeoReportFailure() throws Exception {
//        MetaAnalysisServiceImpl spyService = Mockito.spy(metaAnalysisService);
//        String testUrl = "http://example.com/page";
//
//        Mockito.doThrow(new IOException("Connection failed")).when(spyService).scrape(testUrl);
//
//        List<Setting> settings = Collections.emptyList();
//
//        String report = spyService.generateSeoReport(testUrl, settings);
//
//        assertTrue(report.contains("Failed to generate SEO report:"));
//        assertTrue(report.contains("Connection failed"));
//    }
//}