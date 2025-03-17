//package africa.siteanalysisagent.service;
//
//import africa.siteanalysisagent.dto.AnalysisRequest;
//import africa.siteanalysisagent.model.Setting;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.http.*;
//import org.springframework.test.util.ReflectionTestUtils;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class TelexServiceImplTest {
//
//    @Mock
//    private RestTemplate restTemplate;
//
//    @InjectMocks
//    private TelexServiceImpl telexService;
//
//    private final String webhookUrl = "https://mock-webhook.com";
//
//    private List<Setting> settings;
//
//    @BeforeEach
//    void setUp() {
//        ReflectionTestUtils.setField(telexService, "telexWebhookChannelId", webhookUrl);
//
//        settings = List.of(new Setting(
//                "webhook_url",
//                "text",
//                "Provide your Telex channel webhook URL",
//                true,
//                webhookUrl
//        ));
//    }
//
//    @Test
//    void testNotifyTelex_Success() {
//        String message = "Test message";
//
//        ResponseEntity<String> mockResponse = new ResponseEntity<>("Success", HttpStatus.OK);
//        when(restTemplate.postForEntity(eq(webhookUrl), any(HttpEntity.class), eq(String.class)))
//                .thenReturn(mockResponse);
//
//        ArgumentCaptor<HttpEntity<AnalysisRequest>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
//
//        telexService.notifyTelex(message, settings);
//
//        verify(restTemplate, times(1)).postForEntity(eq(webhookUrl), requestCaptor.capture(), eq(String.class));
//
//        HttpEntity<AnalysisRequest> capturedRequest = requestCaptor.getValue();
//        AnalysisRequest requestBody = capturedRequest.getBody();
//
//        assertNotNull(requestBody);
//        assertEquals("web scrape", requestBody.getEvent_name());
//        assertEquals("site-analyzer", requestBody.getUsername());
//        assertEquals("success", requestBody.getStatus());
//        assertEquals(message, requestBody.getMessage());
//
//        HttpHeaders headers = capturedRequest.getHeaders();
//        assertNotNull(headers);
//        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
//    }
//
//    @Test
//    void testNotifyTelex_MissingWebhookUrl() {
//        String message = "Test message";
//
//        List<Setting> invalidSettings = List.of(
//                new Setting("some_other_label", "text", "Some description", true, "")
//        );
//
//        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
//                telexService.notifyTelex(message, invalidSettings)
//        );
//
//        assertEquals("webhook url is missing", exception.getMessage());
//    }
//
//    @Test
//    void testNotifyTelex_ExceptionHandling() {
//        String message = "Test message";
//
//        when(restTemplate.postForEntity(eq(webhookUrl), any(HttpEntity.class), eq(String.class)))
//                .thenThrow(new RuntimeException("Connection error"));
//
//        assertDoesNotThrow(() -> telexService.notifyTelex(message, settings));
//
//        verify(restTemplate, times(1)).postForEntity(eq(webhookUrl), any(HttpEntity.class), eq(String.class));
//    }
//}