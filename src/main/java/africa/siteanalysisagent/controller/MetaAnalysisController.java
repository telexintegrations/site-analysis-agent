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

//    @PostMapping("/scrape")
//    public ResponseEntity<?> scrapeAndGenerateUrlReport(@RequestBody TelexUserRequest telexUserRequest) throws IOException {
//        Map<String, Object> response = telexServiceIntegration.scrapeAndGenerateUrlReport(telexUserRequest);
//        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), response, "Scrape successful", LocalDate.now()));
//    }

    @PostMapping("/scrape")
    public ResponseEntity<Void> handleWebhook(@RequestBody TelexUserRequest request) throws IOException {
        if (request.text() != null) {
            botService.handleEvent(request);  // Pass the validated request directly
        }

        telexServiceIntegration.scrapeAndGenerateUrlReport(request);
        return ResponseEntity.ok().build();
    }

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