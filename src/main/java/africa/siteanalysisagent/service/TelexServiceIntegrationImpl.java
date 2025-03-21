package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.dto.Button;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotServiceImpl implements BotService {

    private final TelexService telexService;
    private final MetaAnalysisService metaAnalysisService;

    private final Map<String, String> userStates = new HashMap<>(); // Track user state per channelId
    private final Map<String, String> userUrls = new HashMap<>(); // Store URLs per channelId

    @Override
    public void handleEvent(TelexUserRequest userRequest) {
        String text = userRequest.text();
        String channelId = userRequest.channelId();
        String currentState = userStates.getOrDefault(channelId, "idle"); // Default state is "idle"

        log.info("📩 Received input: '{}', ChannelId: '{}', State: '{}'", text, channelId, currentState);

        // Handle the "start" command
        if (text.equalsIgnoreCase("start")) {
            sendWelcomeMessage(channelId);
            userStates.put(channelId, "awaiting_response"); // Update state to "awaiting_response"
            return;
        }

        // Handle "yes" response when the bot is in the "awaiting_response" state
        if (currentState.equals("awaiting_response") && text.equalsIgnoreCase("yes")) {
            sendUrlPrompt(channelId);
            userStates.put(channelId, "awaiting_url"); // Update state to "awaiting_url"
            return;
        }

        // Handle "no" response when the bot is in the "awaiting_response" state
        if (currentState.equals("awaiting_response") && text.equalsIgnoreCase("no")) {
            sendGoodByeMessage(channelId);
            userStates.put(channelId, "idle"); // Reset state to "idle"
            return;
        }

        // Handle URL input when the bot is in the "awaiting_url" state
        if (currentState.equals("awaiting_url")) {
            if (isValidUrl(text)) {
                telexService.sendMessage(channelId, "You entered: " + text + ". Type 'confirm' to start scanning.");
                userUrls.put(channelId, text); // Store the URL for this channelId
                userStates.put(channelId, "awaiting_confirmation"); // Update state to "awaiting_confirmation"
            } else {
                telexService.sendMessage(channelId, "❌ Invalid URL. Please enter a valid URL starting with 'http://' or 'https://'.");
            }
            return;
        }

        // Handle the "confirm" command when the bot is in the "awaiting_confirmation" state
        if (currentState.equals("awaiting_confirmation") && text.equalsIgnoreCase("confirm")) {
            String urlToScan = userUrls.get(channelId);
            if (urlToScan != null) {
                String scanId = UUID.randomUUID().toString();
                telexService.sendMessage(channelId, "📡 Scanning " + urlToScan + "...");
                String report = metaAnalysisService.generateSeoReport(urlToScan, scanId, channelId);
                telexService.sendMessage(channelId, report);
                userStates.put(channelId, "idle"); // Reset state to "idle"
            } else {
                telexService.sendMessage(channelId, "❌ No URL provided. Please enter a valid URL first.");
            }
            return;
        }

        // Handle unrecognized input
        telexService.sendMessage(channelId, "❌ I didn't understand that. Please type 'start' to begin.");
    }

    private void sendWelcomeMessage(String channelId) {
        String message = "Welcome! Would you like to scan your URL?";
        List<Button> buttons = List.of(new Button("Yes", "yes"), new Button("No", "no"));
        telexService.sendInteractiveMessage(channelId, message, buttons);
    }

    private void sendUrlPrompt(String channelId) {
        String message = "Please enter the URL you want to scan:";
        telexService.sendMessage(channelId, message);
    }

    private void sendGoodByeMessage(String channelId) {
        String message = "Goodbye! Let me know if you want help later.";
        telexService.sendMessage(channelId, message);
    }

    private boolean isValidUrl(String text) {
        return text.startsWith("http://") || text.startsWith("https://");
    }
}