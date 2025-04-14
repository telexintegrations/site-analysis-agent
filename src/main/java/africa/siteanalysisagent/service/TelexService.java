package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.Setting;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface TelexService {
    CompletableFuture<ResponseEntity<String>> sendMessage(String channel_id, String message);
    CompletableFuture<ResponseEntity<String>> sendMessage(String channel_id, String message, List<Button> button);



}
