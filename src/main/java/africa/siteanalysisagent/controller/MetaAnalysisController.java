package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.ApiErrorResponse;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.ApiResponse;
import africa.siteanalysisagent.model.TelexIntegration;

import africa.siteanalysisagent.service.TelexServiceIntegration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/meta-analysis")
@RequiredArgsConstructor
public class MetaAnalysisController {

    private final TelexServiceIntegration telexServiceIntegration;


    @PostMapping("/scrape")
    public ResponseEntity<?> scrapeAndGenerateUrlReport(@RequestBody TelexUserRequest telexUserRequest) throws IOException {
        Map<String,Object> response = telexServiceIntegration.scrapeAndGenerateUrlReport(telexUserRequest);
        return ResponseEntity.ok(new ApiResponse<>(
                HttpStatus.OK.value(),
                response,
                "scrape successful",
                LocalDate.now()));

    }


    @GetMapping("/telex")
    public ResponseEntity<?> getTelexConfiguration() {
        try {
            TelexIntegration telexIntegration = telexServiceIntegration.getTelexConfig();
            return ResponseEntity.ok(new ApiResponse<>(
                    HttpStatus.OK.value(),
                    telexIntegration,
                "Telex configuration retrieved successfully",
                LocalDate.now()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ApiErrorResponse(
                            "Failed to retrieve Telex configuration",
                            e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            LocalDate.now().toString()));
        }
    }
}
