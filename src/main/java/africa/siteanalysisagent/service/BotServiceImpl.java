package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.dto.Button;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotServiceImpl implements BotService {

    private final TelexService telexService;
    private final MetaAnalysisService metaAnalysisService;
    private final Map<String, String> userUrls = new ConcurrentHashMap<>();
    private final Map<String, String> userStates = new ConcurrentHashMap<>();
    private static final String BOT_IDENTIFIER = "#bot_message";

    @Override
    public void handleEvent(TelexUserRequest userRequest) {
        String text = userRequest.text();
        String channelId = userRequest.channelId();

        if (shouldSkipMessage(text)) {
            return;
        }

        processUserInput(channelId, text.trim());
    }

    private boolean shouldSkipMessage(String text) {
        return text == null || text.isBlank() || text.contains(BOT_IDENTIFIER);
    }

    private void processUserInput(String channelId, String text) {
        String lowerText = text.toLowerCase();

        if (isUrl(text)) {
            handleUrlInput(channelId, text);
        }
        else if (isConfirmationCommand(lowerText)) {
            handleConfirmation(channelId, lowerText);
        }
        else if (isFixCommand(lowerText)) {
            handleFixCommand(channelId, lowerText);
        }
        else {
            sendHelpMessage(channelId);
        }
    }

    private void handleUrlInput(String channelId, String url) {
        userUrls.put(channelId, url);
        userStates.put(channelId, "awaiting_confirmation");
        sendBotMessage(channelId,
                "🔗 You entered: " + url + "\n\n" +
                        "Please review the URL and:\n" +
                        "👉 Type 'confirm' to start scanning\n" +
                        "👉 Type 'cancel' to enter a different URL");
    }

    @Async
    private void handleConfirmation(String channelId, String command) {
        if (!userUrls.containsKey(channelId)) {
            sendBotMessage(channelId, "⚠️ No URL found! Please enter a URL first.");
            return;
        }

        switch (command) {
            case "confirm" -> startScan(channelId);
            case "cancel" -> cancelScan(channelId);
        }
    }

    @Async
    private void handleFixCommand(String channelId, String command) {
        if (!"awaiting_fix_confirmation".equals(userStates.get(channelId))) {
            sendBotMessage(channelId, "⚠️ No fixes pending confirmation. Start a scan first.");
            return;
        }

        switch (command) {
            case "apply_fixes" -> applyFixes(channelId);
            case "ignore" -> ignoreFixes(channelId);
            default -> sendBotMessage(channelId, "⚠️ Invalid command. Type 'apply_fixes' or 'ignore'");
        }
    }

    @Async
    private void startScan(String channelId) {
        if ("scanning".equals(userStates.get(channelId))) {
            sendBotMessage(channelId, "⚠️ A scan is already in progress! Please wait...");
            return;
        }

        String urlToScan = userUrls.get(channelId);
        userStates.put(channelId, "scanning");

        sendBotMessage(channelId, "🔍 Scanning: " + urlToScan + "...\n⏳ This may take a few moments...");

        String scanId = UUID.randomUUID().toString();
        metaAnalysisService.generateSeoReport(urlToScan, scanId, channelId);

        userStates.put(channelId, "awaiting_fix_confirmation");
    }

    @Async
    private void applyFixes(String channelId) {
        String optimizedMetags = metaAnalysisService.getOptimizedMetags(channelId);
        sendBotMessage(channelId, "🛠️ Applying optimized meta tags...");

        // Here you would actually apply the fixes to the website
        // For now we'll just show the optimized tags
        sendBotMessage(channelId, "✅ Here are your optimized meta tags:\n" + optimizedMetags);

        completeScan(channelId);
    }

    @Async
    private void ignoreFixes(String channelId) {
        sendBotMessage(channelId, "✅ Okay, keeping original meta tags.");
        completeScan(channelId);
    }

    private void completeScan(String channelId) {
        metaAnalysisService.clearOptimizedMetags(channelId);
        userStates.remove(channelId);
        userUrls.remove(channelId);
        sendBotMessage(channelId, "Type a new URL to start another scan.");
    }

    private void cancelScan(String channelId) {
        userUrls.remove(channelId);
        userStates.remove(channelId);
        sendBotMessage(channelId, "🚫 Scan canceled. Please enter a new URL to start again.");
    }

    private void sendHelpMessage(String channelId) {
        sendBotMessage(channelId,
                "❌ Invalid command\n\n" +
                        "Available commands:\n" +
                        "• Enter a URL to start\n" +
                        "• 'confirm' - confirm scan\n" +
                        "• 'cancel' - cancel current scan\n" +
                        "• 'apply_fixes' - apply recommended fixes\n" +
                        "• 'ignore' - skip applying fixes");
    }

    private boolean isUrl(String text) {
        return text.matches("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$");
    }

    private boolean isConfirmationCommand(String text) {
        return text.equals("confirm") || text.equals("cancel");
    }

    private boolean isFixCommand(String text) {
        return text.equals("apply_fixes") || text.equals("ignore");
    }

    private void sendBotMessage(String channelId, String message) {
        String botTaggedMessage = message + " " + BOT_IDENTIFIER;
        telexService.sendMessage(channelId, botTaggedMessage)
                .exceptionally(e -> {
                    log.error("Failed to send message to channel {}: {}", channelId, e.getMessage());
                    return null;
                });
    }
}