package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.ApiErrorResponse;
import africa.siteanalysisagent.dto.Setting;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.ApiResponse;
import africa.siteanalysisagent.model.TelexIntegration;
import africa.siteanalysisagent.service.BotService;
import africa.siteanalysisagent.service.TelexService;
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
    private final TelexService telexService;

    @PostMapping("/scrape")
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
        try {
            // 1. Extract channel ID
            String channelId = payload.containsKey("channel_id")
                    ? payload.get("channel_id").toString()
                    : "default-channel-id";

            // 2. Extract and update webhook URL
            if (payload.containsKey("settings")) {
                List<Map<String, Object>> settings = (List<Map<String, Object>>) payload.get("settings");
                telexService.updateWebhookUrl(channelId, convertToSettings(settings));
            }

            // 3. Process message
            String message = extractMessage(payload);
            if (message != null && !message.isBlank()) {
                TelexUserRequest request = new TelexUserRequest(message, channelId, List.of());
                botService.handleEvent(request);
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private List<Setting> convertToSettings(List<Map<String, Object>> rawSettings) {
        return rawSettings.stream()
                .map(setting -> new Setting(
                        (String) setting.get("label"),
                        (String) setting.get("type"),
                        (String) setting.get("description"),
                        (String) setting.get("default"),
                        (Boolean) setting.get("required")
                ))
                .toList();
    }

    private String extractMessage(Map<String, Object> payload) {
        if (payload.containsKey("message") && payload.get("message") instanceof String) {
            return ((String) payload.get("message")).replaceAll("<[^>]+>", "").trim();
        }
        return null;
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