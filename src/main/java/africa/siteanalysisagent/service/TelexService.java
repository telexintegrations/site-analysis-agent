package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.AnalysisRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelexService {
    private final String TELEX_CHANNEL_WEBHOOK = "https://ping.telex.im/v1/webhooks/";

    private final RestTemplate restTemplate;

    @Value("${telex.channel.id}")
    private String channelId;

    public void notifyTelex(String message) {
        String channelHookUrl = TELEX_CHANNEL_WEBHOOK + channelId;

        AnalysisRequest requestData = AnalysisRequest.builder()
                .event_name("web scrape")
                .username("site-analyzer")
                .status("success")
                .message(message)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<AnalysisRequest> entity = new HttpEntity<>(requestData, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(channelHookUrl, entity, String.class);
            log.info("Response code: {}", response.getStatusCode());
            log.info("Response: {}", response);
        } catch (Exception ex) {
            log.error("Error notifying telex: {}", ex.getMessage());
        }
    }
}
