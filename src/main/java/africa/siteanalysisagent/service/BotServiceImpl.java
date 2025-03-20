package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.dto.TelexEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotService {

    private final TelexService telexService;
    private final MetaAnalysisService metaAnalysisService;

    public void handleEvent (TelexEvent event){
        String text = event.getText();
        String webhookUrl = event.getWebhookUrl();
        if(webhookUrl == null || webhookUrl.isEmpty()){
            webhookUrl = "default-channel-id"; // Ensure a valid channel ID
        }


        if (text.equalsIgnoreCase("start")) {
            sendWelcomeMessage(webhookUrl);
        } else if (text.equalsIgnoreCase("yes")) {
            sendUrlPrompt(webhookUrl);
        } else if (text.equalsIgnoreCase("no")) {
            sendGoodByeMessage(webhookUrl);
        } else if (isValidUrl(text)) {
            // Ask for confirmation before starting the analysis
            sendConfirmationPrompt(webhookUrl, text);
        } else if (text.equalsIgnoreCase("confirm")) {
            // If user confirms, start the analysis
            String scanId = UUID.randomUUID().toString(); // Unique scan ID
            telexService.notifyTelex(webhookUrl, "📡 **Your Scan ID:** `" + scanId + "`\nTracking progress now...");

            String userId = UUID.randomUUID().toString(); // Generate userId for future tracking

            String report = metaAnalysisService.generateSeoReport(text, scanId, webhookUrl);

            // Future: Save to DB (once added)
            // dbService.saveScan(userId, scanId, text);

            telexService.notifyTelex(webhookUrl, report);
        }
    }

    private void sendWelcomeMessage(String userId){
        String message = " Welcome! would you like to scan your URL?";
        List<Button> buttons = List.of(new Button("Yes","yes"), new Button("No","no"));
        telexService.sendInteractiveMessage(userId,message,buttons);
    }

    private void sendUrlPrompt(String userId){
        String message = "Please enter the URL you want to scan:";
        telexService.sendMessage(userId, message);
    }

    private void sendConfirmationPrompt(String userId, String url) {
        String message = "You entered the following URL:\n`" + url + "`\n\nDo you want to start the analysis?";
        List<Button> buttons = List.of(new Button("Confirm", "confirm"), new Button("Cancel", "cancel"));
        telexService.sendInteractiveMessage(userId, message, buttons);
    }

    private void sendGoodByeMessage(String userId){
        String message = "Goodbye! Let me know if you want help later";
        telexService.sendMessage(userId, message);
    }

    private boolean isValidUrl(String text){
        return text.startsWith("http://") || text.startsWith("https://");
    }

}

