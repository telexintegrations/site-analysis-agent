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
    public ResponseEntity<?> scrapeAndGenerateUrlReport(@RequestBody TelexUserRequest telexUserRequest) throws IOException {

        try {
            log.info("Received request from channel {}: {}", telexUserRequest.channelId(), telexUserRequest.text());

            // Process the request asynchronously
            telexServiceIntegration.scrapeAndGenerateUrlReport(telexUserRequest);

            // Return just an acknowledgment
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing request", e);
            return ResponseEntity.internalServerError().body("Error processing request");
        }
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
