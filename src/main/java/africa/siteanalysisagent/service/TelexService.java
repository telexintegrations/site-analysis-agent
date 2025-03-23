package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TelexService {
    CompletableFuture<ResponseEntity<String>> sendMessage(String channelId, String message);
    CompletableFuture<ResponseEntity<String>>  sendInteractiveMessage(String channelId, String message, List<Button> buttons);
    CompletableFuture<ResponseEntity<String>> notifyTelex(String message, String channelId);
}
