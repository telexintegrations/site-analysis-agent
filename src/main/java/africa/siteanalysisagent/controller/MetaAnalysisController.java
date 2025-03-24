package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.ApiErrorResponse;
import africa.siteanalysisagent.dto.Setting;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.ApiResponse;
import africa.siteanalysisagent.model.TelexIntegration;
import africa.siteanalysisagent.service.BotService;
import africa.siteanalysisagent.service.TelexServiceIntegration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meta-analysis")
@RequiredArgsConstructor
@Slf4j
public class MetaAnalysisController {

    private final TelexServiceIntegration telexServiceIntegration;
    private final BotService botService;

    @PostMapping("/scrape")
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> rawRequest) throws IOException {
        log.info("📩 Raw Telex Payload: {}", rawRequest);

        // Extract text from potential nested structures
        String text = extractTextFromPayload(rawRequest);
        String channelId = extractChannelIdFromPayload(rawRequest);

        if (text == null || text.isBlank()) {
            log.warn("⚠️ Empty text received from Telex. Payload: {}", rawRequest);
            return ResponseEntity.badRequest().build();
        }

        TelexUserRequest request = new TelexUserRequest(text, channelId, List.of());
        botService.handleEvent(request);
        return ResponseEntity.ok().build();
    }

    private String extractTextFromPayload(Map<String, Object> payload) {
        // Case 1: HTML message content
        if (payload.containsKey("message") && payload.get("message") instanceof String) {
            String htmlMessage = (String) payload.get("message");
            return htmlMessage.replaceAll("<[^>]+>", "").trim(); // Strip HTML tags
        }
        // Case 2: Normal text field
        if (payload.containsKey("text")) {
            return payload.get("text").toString();
        }
        return null;
    }
    private String extractChannelIdFromPayload(Map<String, Object> payload) {
        // Try common channel ID field names
        if (payload.containsKey("channel_id")) {
            return payload.get("channel_id").toString();
        }
        if (payload.containsKey("channelId")) {
            return payload.get("channelId").toString();
        }
        return "default-channel-id";
    }

//    @PostMapping("/webhook")
//    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String,String> payload) throws IOException {
//
//        String text = payload.get("text");
//        String channelId = payload.get("channel_id"); // Extract channel ID
//
//
//        if (text != null) {
//            TelexUserRequest telex = new TelexUserRequest(text, channelId, List.of());
//            telex.text();
//            telex.channelId(); // Ensure channelId is never null
//            botService.handleEvent(telex);
//        }
//        Map<String, Object> response = telexServiceIntegration.scrapeAndGenerateUrlReport(new TelexUserRequest(text,channelId,List.of()));
//        return ResponseEntity.ok().build();
//    }

    @GetMapping("/telex")
    public ResponseEntity<?> getTelexConfiguration() {
        try {
            TelexIntegration telexIntegration = telexServiceIntegration.getTelexConfig();
            return ResponseEntity.ok(telexIntegration);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ApiErrorResponse("Failed to retrieve Telex configuration", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value(), LocalDate.now().toString())
            );
        }
    }
}