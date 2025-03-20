package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.AnalysisRequest;
import africa.siteanalysisagent.dto.Button;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelexServiceImpl implements TelexService {

    private final RestTemplate restTemplate;

    public TelexServiceImpl() {
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate(){
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(50000); // 20 seconds
        factory.setReadTimeout(100000); // 20 seconds
        return new RestTemplate(factory);
    }



    @Override
    public void sendMessage(String channelId, String message) {
        notifyTelex(message, channelId); // Assuming channelId is the webhook URL
    }

    @Override
    public void sendInteractiveMessage(String webhookUrl, String message, List<Button> buttons) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("Cannot send message: channel_id is missing.");
            return;
        }

        Map<String, Object> payload = createPayload(message, buttons);
        sendPayloadToTelex(webhookUrl, payload);
    }

    @Override
    public void notifyTelex(String message, String webhookUrl) {
        AnalysisRequest requestData = AnalysisRequest.builder()
                .event_name("web scrape")
                .username("site-analyzer")
                .status("success")
                .message(message)
                .build();

        HttpHeaders headers = createHeaders();
        HttpEntity<AnalysisRequest> entity = new HttpEntity<>(requestData, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Response code: {}", response.getStatusCode());
            log.info("Response: {}", response);
        } catch (Exception ex) {
            log.error("Error notifying telex: {}", ex.getMessage());
        }
    }

    private Map<String, Object> createPayload(String message, List<Button> buttons) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);
        payload.put("buttons", formatButtons(buttons));
        return payload;
    }

    private List<Map<String, String>> formatButtons(List<Button> buttons) {
        List<Map<String, String>> buttonList = new ArrayList<>();
        for (Button button : buttons) {
            Map<String, String> btn = new HashMap<>();
            btn.put("text", button.getText());
            btn.put("value", button.getValue());
            buttonList.add(btn);
        }
        return buttonList;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private void sendPayloadToTelex(String webhookUrl, Map<String, Object> payload) {
        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        log.info("Sending payload to Telex: {}", payload);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Telex Response: Status code = {}, Body = {}", response.getStatusCode(), response.getBody());
        } catch (Exception ex) {
            log.error("Failed to send Telex notification: {}", ex.getMessage());
        }
    }
}