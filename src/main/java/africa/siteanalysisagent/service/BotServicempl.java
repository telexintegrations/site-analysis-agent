package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.dto.Button;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class BotServicempl implements BotService {

    private final TelexService telexService;
    private final MetaAnalysisService metaAnalysisService;

    private final Map<String, String> userUrls = new HashMap<>();

    @Override
    public void handleEvent(TelexUserRequest userRequest) {
        String text = userRequest.text().trim();
        String channelId = userRequest.channelId();

        log.info("📩 Received message: '{}' from '{}'", text, channelId);

        if (text.equalsIgnoreCase("start")) {
            telexService.sendMessage(channelId, "👋 Hello! Would you like to scan a URL?\n👉 Type 'yes' to continue or 'no' to cancel.");
            return;
        }

        if (text.equalsIgnoreCase("yes")) {
            telexService.sendMessage(channelId, "✅ Please enter the URL you want to scan.");
            return;
        }

        if (text.equalsIgnoreCase("no")) {
            telexService.sendMessage(channelId, "❌ Okay! Let me know if you need anything else.");
            return;
        }

        if (isValidUrl(text)) {
            userUrls.put(channelId, text);
            telexService.sendMessage(channelId, "🔗 You entered: " + text + "\n👉 Type 'confirm' to start scanning or 'cancel' to enter a new URL.");
            return;
        }

        if (text.equalsIgnoreCase("confirm")) {
            if (!userUrls.containsKey(channelId)) {
                telexService.sendMessage(channelId, "⚠️ No URL found! Please enter a valid URL first.");
                return;
            }

            String urlToScan = userUrls.get(channelId);
            telexService.sendMessage(channelId, "🔍 Scanning: " + urlToScan + "...\n⏳ Please wait...");

            userUrls.remove(channelId);
            return;
        }

        if (text.equalsIgnoreCase("cancel")) {
            userUrls.remove(channelId);
            telexService.sendMessage(channelId, "🚫 URL entry canceled. Please enter a new URL.");
            return;
        }

    }

    private boolean isValidUrl(String text) {
        return text.matches("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$");
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

}