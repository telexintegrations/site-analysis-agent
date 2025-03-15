package africa.siteanalysisagent.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetaAnalysisServiceImplTest {

    @Mock
    private TelexServiceImpl telexService;

    @InjectMocks
    private MetaAnalysisServiceImpl metaAnalysisService;


    @Test
    void testIsSingleUrl_ValidSinglePage() {
        assertTrue(metaAnalysisService.isSingleUrl("https://example.com/page"));
    }

    @Test
    void testScrape_ValidUrl() throws IOException {
        String url = "https://example.com/page";

        Connection mockConnection = Mockito.mock(Connection.class);

        Document mockDocument = Jsoup.parse("<html><head><title>Test</title></head></html>");

        try (var jsoupMock = Mockito.mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(mockConnection);
            Mockito.when(mockConnection.timeout(10000)).thenReturn(mockConnection);
            Mockito.when(mockConnection.get()).thenReturn(mockDocument);

            Document result = metaAnalysisService.scrape(url);

            assertNotNull(result);
            assertEquals("Test", result.title());
        }
    }

    @Test
    void testCheckMetaTags_AllMetaTagsPresent() {
        Document document = Jsoup.parse("<html><head><title>Title</title><meta name=\"description\" content=\"Meta description\"/><meta name=\"keyword\" content=\"keywords\"/></head></html>");
        List<String> issues = metaAnalysisService.checkMetaTags(document);
        assertEquals(3, issues.size());
        assertTrue(issues.get(0).contains("Title tag: Title"));
        assertTrue(issues.get(1).contains("Meta Description: Meta description"));
        assertTrue(issues.get(2).contains("Meta Keywords: keywords"));
    }

    @Test
    void testGenerateSeoReport_Success() throws IOException {
        String url = "https://example.com/page";
        Document mockDocument = Jsoup.parse("<html><head><title>Test</title></head></html>");

        Connection mockConnection = Mockito.mock(Connection.class);

        try (var jsoupMock = Mockito.mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(mockConnection);

            when(mockConnection.timeout(10000)).thenReturn(mockConnection);
            when(mockConnection.get()).thenReturn(mockDocument);

            doNothing().when(telexService).notifyTelex(anyString());

            String report = metaAnalysisService.generateSeoReport(url);

            assertTrue(report.contains("SEO Analysis Report for: " + url));
            verify(telexService, times(1)).notifyTelex(anyString());
        }
    }

    @Test
    void testGenerateSeoReport_InvalidUrl() {
        String url = "invalid_url";
        String report = metaAnalysisService.generateSeoReport(url);
        assertTrue(report.startsWith("Failed to generate SEO report:"));
    }
}