package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.AnalysisRequest;
import africa.siteanalysisagent.model.Setting;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelexServiceImpl implements TelexService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telex.channel.webhook.channel.id}")
    private String telexWebhookChannelId;


    @Override
    public void notifyTelex(String message, List<Setting> settings) {



        AnalysisRequest requestData = AnalysisRequest.builder()
                .event_name("web scrape")
                .username("site-analyzer")
                .status("success")
                .message(message)
                .build();

        Optional<String> telexwebhookopt = settings.stream()
                .filter(s -> "webhook_url".equals(s.label()))
                .map(Object::toString)
                .findFirst();

        if(telexwebhookopt.isEmpty()){
            throw new IllegalArgumentException("Telex webhook url is null");
        }

        String telexwebhook = telexwebhookopt.get();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<AnalysisRequest> entity = new HttpEntity<>(requestData, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(telexwebhook, entity, String.class);
            log.info("Response code: {}", response.getStatusCode());
            log.info("Response: {}", response);
        } catch (Exception ex) {
            log.error("Error notifying telex: {}", ex.getMessage());
        }
    }
}
