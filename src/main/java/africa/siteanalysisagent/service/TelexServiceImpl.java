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

@Service
@Slf4j
@RequiredArgsConstructor
public class TelexServiceImpl implements TelexService {

    private final RestTemplate restTemplate;

    public TelexServiceImpl() {
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(50000);
        factory.setReadTimeout(100000);
        return new RestTemplate(factory);
    }

    @Override
    public void sendMessage(String channelId, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);
        payload.put("channel_id", channelId);

        sendToTelex(payload,channelId);
    }

    @Override
    public void sendInteractiveMessage(String channelId, String message, List<Button> buttons) {
        if (channelId == null || channelId.isEmpty()) {
            log.error("Cannot send message: channel_id is missing.");
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);
        payload.put("channel_id", channelId);

        // Properly format buttons
        List<Map<String, String>> buttonList = new ArrayList<>();
        for (Button button : buttons) {
            Map<String, String> btn = new HashMap<>();
            btn.put("text", button.getText());
            btn.put("value", button.getValue());
            buttonList.add(btn);
        }
        payload.put("buttons", buttonList);

        sendToTelex(payload, channelId);
    }

    @Override
    public void notifyTelex(String message, String channelId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_name", "web_scrape");
            payload.put("username", "site-analyzer");
            payload.put("status", "success");
            payload.put("message", message);  // Fixed typo from "massage" to "message"
            payload.put("channel_id", channelId);

            sendToTelex(payload, channelId);
        } catch (Exception e) {
            log.error("Failed to send Telex notification: {}", e.getMessage(), e);
        }
    }

    private void sendToTelex(Map<String, Object> payload, String channelId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("accept", "application/json");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            log.info("📤 Sending message to Telex: {}", payload);

            ResponseEntity<String> response = restTemplate.postForEntity(channelId, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Telex Response: {}", response.getBody());
            } else {
                log.error("❌ Telex API Error: Status = {}, Response = {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Failed to send message to Telex: {}", e.getMessage(), e);
        }
    }
}
