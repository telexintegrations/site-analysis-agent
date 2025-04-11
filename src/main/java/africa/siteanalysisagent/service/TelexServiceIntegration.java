package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;

public interface TelexServiceIntegration {
    TelexIntegration getTelexConfig() throws JsonProcessingException;

    public ResponseEntity<Map<String, Object>> scrapeAndGenerateUrlReport(TelexUserRequest telexUserRequest) throws IOException;

    public ResponseEntity<Map<String, Object>> handleTelexWebhook(
            String channelId,
            String webhookToken,
            String message);
}