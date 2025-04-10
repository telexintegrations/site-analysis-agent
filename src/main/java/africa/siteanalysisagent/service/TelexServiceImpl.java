package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.Setting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableAsync // Enable asynchronous execution
public class TelexServiceImpl implements TelexService {

    private static final String TELEX_WEBHOOK_BASE = "https://ping.telex.im/v1/webhooks";
    private final RestTemplate restTemplate;
    private final Map<String, CompletableFuture<Void>> channelQueues= new ConcurrentHashMap<>();
    private final Executor telexExecutor = Executors.newFixedThreadPool(4);

    public TelexServiceImpl() {
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @Override
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message, List<Button> button) {
        return enqueueForChannel(channelId, () -> {
            String webhookUrl = TELEX_WEBHOOK_BASE + "/" + channelId;

            return CompletableFuture.supplyAsync(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    Map<String, Object> payload = createPayload(channelId, message, button);
                    log.info("sending to channel {} : {}", channelId, message);

                    ResponseEntity<String> response = restTemplate.postForEntity(
                            webhookUrl,
                            new HttpEntity<>(payload, headers),
                            String.class
                    );

                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("Delivered to channeel {}", channelId);
                    } else {
                        log.error("Delivery failed to {}: {}", channelId, response.getBody());
                    }
                    return response;
                } catch (Exception e) {
                    log.error("Error sending to channel {}", channelId, e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to send message");
                }
            }, telexExecutor);
            });
            }


    @Override
    public CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message) {
        return sendMessage(channelId, message, null);
    }

    private Map<String, Object> createPayload(String channelId, String message, List<Button> button) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_name", "web_scraper");
        payload.put("username", "site-analyzer");
        payload.put("status", "success");
        payload.put("message", message);
        payload.put("channel_id", channelId);

        if (button != null && !button.isEmpty()) {
            payload.put("buttons", formatButtonsForTelex(button));
        }
        return payload;
    }

    private List<Map<String, String>> formatButtonsForTelex(List<Button> buttons) {
        return buttons.stream()
                .map(button -> {
                    Map<String, String> btnMap = new HashMap<>();
                    btnMap.put("text", button.getText());
                    btnMap.put("value", button.getValue());
                    btnMap.put("action", button.getAction());
                    return btnMap;
                })
                .collect(Collectors.toList());
    }


    public void updateWebhookUrl(String channelId, List<Setting> settings) {
        log.info("Webhook URL for channel {} will be constructed dynamically", channelId);

    }



    // Maintain your original sequencing logic
    private CompletableFuture<ResponseEntity<String>> enqueueForChannel(
            String channelId,
            Supplier<CompletableFuture<ResponseEntity<String>>> operation) {

        CompletableFuture<ResponseEntity<String>> result = new CompletableFuture<>();

        channelQueues.compute(channelId, (key, existingFuture) -> {
            CompletableFuture<Void> lastFuture = existingFuture != null
                    ? existingFuture
                    : CompletableFuture.completedFuture(null);

            return lastFuture.thenComposeAsync(v -> operation.get()
                            .thenAccept(result::complete)
                            .exceptionally(ex -> {
                                result.completeExceptionally(ex);
                                return null;
                            }),
                    telexExecutor
            );
        });

        return result;
    }

}