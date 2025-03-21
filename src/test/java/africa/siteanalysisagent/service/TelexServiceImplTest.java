//package africa.siteanalysisagent.service;
//
//import africa.siteanalysisagent.dto.AnalysisRequest;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.http.*;
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
//
//
//    @Test
//    void testNotifyTelex_Success() {
//        String message = "Test message";
//        ResponseEntity<String> dummyResponse = new ResponseEntity<>("OK", HttpStatus.OK);
//        when(restTemplate.postForEntity(eq(webhookUrl), any(HttpEntity.class), eq(String.class)))
//                .thenReturn(dummyResponse);
//
//        telexService.notifyTelex(message, webhookUrl);
//
//        ArgumentCaptor<HttpEntity<AnalysisRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);
//        verify(restTemplate, times(1))
//                .postForEntity(eq(webhookUrl), captor.capture(), eq(String.class));
//        HttpEntity<AnalysisRequest> capturedEntity = captor.getValue();
//
//        HttpHeaders headers = capturedEntity.getHeaders();
//        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
//        assertEquals(List.of(MediaType.APPLICATION_JSON), headers.getAccept());
//
//        AnalysisRequest requestData = capturedEntity.getBody();
//        assertNotNull(requestData);
//        assertEquals("web scrape", requestData.getEvent_name());
//        assertEquals("site-analyzer", requestData.getUsername());
//        assertEquals("success", requestData.getStatus());
//        assertEquals(message, requestData.getMessage());
//    }
//
//    @Test
//    void notifyTelex_ExceptionHandled() {
//        String message = "Test message";
//        when(restTemplate.postForEntity(eq(webhookUrl), any(HttpEntity.class), eq(String.class)))
//                .thenThrow(new RuntimeException("Test exception"));
//
//        assertDoesNotThrow(() -> telexService.notifyTelex(message, webhookUrl));
//
//        verify(restTemplate, times(1))
//                .postForEntity(eq(webhookUrl), any(HttpEntity.class), eq(String.class));
//    }
//
//    @Test
//    void notifyTelex_NullMessage() {
//        ResponseEntity<String> dummyResponse = new ResponseEntity<>("OK", HttpStatus.OK);
//        when(restTemplate.postForEntity(eq(webhookUrl), any(HttpEntity.class), eq(String.class)))
//                .thenReturn(dummyResponse);
//
//        telexService.notifyTelex(null, webhookUrl);
//
//        ArgumentCaptor<HttpEntity<AnalysisRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);
//        verify(restTemplate).postForEntity(eq(webhookUrl), captor.capture(), eq(String.class));
//        AnalysisRequest requestData = captor.getValue().getBody();
//        assertNotNull(requestData);
//        assertNull(requestData.getMessage());
//    }
//}