package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.Setting;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TelexService {
    CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message);
    CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message, List<Button> button);
    void updateWebhookUrl(String channelId, List<Setting> settings);



}
