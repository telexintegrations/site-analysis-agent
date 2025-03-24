package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.*;
import africa.siteanalysisagent.model.*;
import africa.siteanalysisagent.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/meta-analysis")
@RequiredArgsConstructor
@Slf4j
public class MetaAnalysisController {

    private final TelexServiceIntegration telexService;
    private final BotService botService;
    private final TelexService telexWebhookService;

    @PostMapping("/scrape")
    public ResponseEntity<Void> handleWebhook(@RequestBody WebhookPayload payload) {
        try {
            // Extract and validate core data
            WebhookData data = payload.extractValidData();

            // Update webhook configuration if settings exist
            if (payload.hasSettings()) {
                telexWebhookService.updateWebhookUrl(data.channelId(), payload.getSettings());
            }

            // Process message if valid
            if (data.hasValidMessage()) {
                botService.handleEvent(new TelexUserRequest(
                        data.message(),
                        data.channelId(),
                        data.username(),
                        List.of()
                ));
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/telex")
    public ResponseEntity<?> getTelexConfiguration() {
        try {
            return ResponseEntity.ok(telexService.getTelexConfig());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiErrorResponse(
                            "Configuration error",
                            e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            LocalDate.now().toString()
                    ));
        }
    }

    // Inner DTO for payload handling
    private record WebhookPayload(
            Object message,
            Object text,
            String channel_id,
            String channelId,
            String username,
            List<Map<String, Object>> settings
    ) {
        public WebhookData extractValidData() {
            return new WebhookData(
                    extractCleanMessage(),
                    determineChannelId(),
                    username != null ? username : "unknown"
            );
        }

        private String extractCleanMessage() {
            String raw = message instanceof String ? (String) message :
                    text instanceof String ? (String) text : null;
            return raw != null ? raw.replaceAll("<[^>]+>", "").trim() : null;
        }

        private String determineChannelId() {
            return channel_id != null ? channel_id :
                    channelId != null ? channelId : "default-channel-id";
        }

        public boolean hasSettings() {
            return settings != null && !settings.isEmpty();
        }

        public List<Setting> getSettings() {
            return settings.stream()
                    .map(setting -> new Setting(
                            (String) setting.get("label"),
                            (String) setting.get("type"),
                            (String) setting.get("description"),
                            (String) setting.get("default"),
                            (Boolean) setting.get("required")
                    ))
                    .toList();
        }
    }

    private record WebhookData(
            String message,
            String channelId,
            String username
    ) {
        public boolean hasValidMessage() {
            return message != null && !message.isBlank();
        }
    }
}