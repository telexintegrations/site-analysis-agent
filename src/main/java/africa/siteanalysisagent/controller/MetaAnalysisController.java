package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.*;
import africa.siteanalysisagent.model.ChatMessage;
import africa.siteanalysisagent.model.ChatResponse;
import africa.siteanalysisagent.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/meta-analysis")
@RequiredArgsConstructor
@Slf4j
public class MetaAnalysisController {

    private final LynxService lynxService;
    private final TelexServiceIntegration telexService;

    @PostMapping("/interact")
    public ChatResponse handleUserMessage(@Valid
            @RequestBody ChatMessage chatMessage) {

        //validate input
        if(chatMessage.getUserMessage() == null || chatMessage.getUserMessage().isBlank()){
          return ChatResponse.error("Message cannot be blank");
        }

        // Set default user ID if not provided
        if (chatMessage.getUserId() == null) {
            chatMessage.setUserId("guest-" + UUID.randomUUID());
        }

        // Set timestamp if not provided
        if (chatMessage.getTimestamp() == null) {
            chatMessage.setTimestamp(LocalDateTime.now());
        }

        try {
            ChatResponse response = lynxService.processMessage(chatMessage);
            return ResponseEntity.ok(response).getBody();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.error("Processing failed: " + e.getMessage())).getBody();
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
}

