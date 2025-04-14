package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelexServiceImpl implements TelexService {

    private static final String TELEX_WEBHOOK_BASE = "https://ping.telex.im/v1/webhooks";
    private final RestTemplate restTemplate;
    private final Map<String, String> channelTokens = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> channelQueues = new ConcurrentHashMap<>();
    private final Executor telexExecutor = Executors.newFixedThreadPool(4);

    public TelexServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message) {
        return sendMessage(channelId, message, null);
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message, List<Button> buttons) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String token = channelTokens.get("01961ccd-cf6a-7891-afc6-260274df9a90");
                if (token == null) {
                    log.error("Channel {} not registered", channelId);
                    return ResponseEntity.badRequest().body("Channel not registered");
                }

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("event_name", "web_scraper");
                payload.put("username", "site-analyzer");
                payload.put("status", "success");
                payload.put("channel_id", channelId);
                payload.put("message", message);
                payload.put("timestamp", System.currentTimeMillis());

                if (buttons != null && !buttons.isEmpty()) {
                    payload.put("buttons", buttons.stream()
                            .map(btn -> Map.of(
                                    "text", btn.getText(),
                                    "value", btn.getValue(),
                                    "action", btn.getAction()
                            ))
                            .toList()
                    );
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        TELEX_WEBHOOK_BASE + "/" + token,
                        new HttpEntity<>(payload, headers),
                        String.class
                );

                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.error("Failed to send to channel {}: {}", channelId, response.getBody());
                }
                return response;

            } catch (Exception e) {
                log.error("Error sending to channel {}", channelId, e);
                return ResponseEntity.internalServerError().body("Failed to send message");
            }
        }, telexExecutor);
    }

    public void registerChannel(String channelId, String webhookToken) {
        channelTokens.put(channelId, webhookToken);
        log.info("Registered channel {}", channelId);
    }


}