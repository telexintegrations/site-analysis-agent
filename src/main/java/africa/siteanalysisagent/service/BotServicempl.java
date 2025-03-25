package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.dto.Button;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotServicempl implements BotService {

    private final TelexService telexService;
    private final MetaAnalysisService metaAnalysisService;
    private final Map<String, String> userUrls = new ConcurrentHashMap<>();
    private final Map<String, String> pendingOptimizations = new ConcurrentHashMap<>();
    private final Map<String, String> userStates = new ConcurrentHashMap<>();


    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    // Bot message identifier - added at the end of ALL bot messages
    private static final String BOT_IDENTIFIER = "#bot_message";


    @Override
    public void handleEvent(TelexUserRequest userRequest) {
        String text = userRequest.text();
        String channelId = userRequest.channelId();

        // Skip processing if:
        // 1. Message is empty/null
        // 2. Message contains our identifier (means it's from the bot)
        if (text == null || text.isBlank() || text.contains(BOT_IDENTIFIER)) {
            log.debug("Skipping bot message or empty input");
            return;
        }

        asyncExecutor.submit(() -> processUserInput(channelId, text.trim()));
    }

    private void processUserInput(String channelId, String text) {
        switch (text.toLowerCase()) {
            case "start" -> sendBotMessage(channelId, "\uD83D\uDC4B Hello! Would you like to scan a URL?\n\uD83D\uDC49 Type 'yes' to continue or 'no' to cancel.");
            case "yes" -> sendBotMessage(channelId, "✅ Please enter the URL you want to scan.");
            case "no" -> sendBotMessage(channelId, "❌ Okay! Let me know if you need anything else.");
            case "cancel" -> {
                userUrls.remove(channelId);
                sendBotMessage(channelId, "🚫 URL entry canceled. Please enter a new URL.");
            }
            case "confirm" -> asyncExecutor.submit(() -> handleUrlConfirmation(channelId));
            case "apply_fixes", "ignore" -> asyncExecutor.submit(() -> handleFixConfirmation(channelId, text));
            default -> handleDefaultInput(channelId, text);
        }
    }


    @Async
    private void handleUrlConfirmation(String channelId) {
        if (!userUrls.containsKey(channelId)) {
            sendBotMessage(channelId, "⚠️ No URL found! Please enter a valid URL first.");
            return;
        }
        if ("scanning".equals(userStates.get(channelId))) {
            sendBotMessage(channelId, "⚠️ A scan is already in progress! Please wait...");
            return;
        }
        userStates.put(channelId, "scanning");
        String urlToScan = userUrls.get(channelId);
        sendBotMessage(channelId, "🔍 Scanning: " + urlToScan + "...\n⏳ Please wait...");
        String scanId = UUID.randomUUID().toString();
        asyncExecutor.submit(() -> metaAnalysisService.generateSeoReport(urlToScan, scanId, channelId));
        userStates.put(channelId, "awaiting_fix_confirmation");
        userUrls.remove(channelId);
    }

    private void handleDefaultInput(String channelId, String text) {
        if (isValidUrl(text)) {
            userUrls.put(channelId, text);
            userStates.put(channelId, "waiting_for_confirmation");
            sendBotMessage(channelId, "🔗 You entered: " + text + "\n\uD83D\uDC49 Type 'confirm' to start scanning or 'cancel' to enter a new URL.");
        } else {
            sendBotMessage(channelId, "❌ Invalid command or URL. Please type 'start' to begin.");
        }
    }

    @Async
    private void handleFixConfirmation(String channelId, String userInput) {
        if (userInput.equalsIgnoreCase("apply_fixes")) {
            String optimizedMetags = metaAnalysisService.getOptimizedMetags(channelId);
            sendBotMessage(channelId, "🤖 **Optimized Meta Tags:**\n" + optimizedMetags);
            userStates.remove(channelId);
        } else if (userInput.equalsIgnoreCase("ignore")) {
            sendBotMessage(channelId, "✅ AI fixes ignored. Let me know if you need further assistance.");
            metaAnalysisService.clearOptimizedMetags(channelId);
            userStates.remove(channelId);
        }
    }

    private void sendBotMessage(String channelId, String message) {
        String botTaggedMessage = message + " " + BOT_IDENTIFIER;
        telexService.sendMessage(channelId, botTaggedMessage).exceptionally(e -> {
            log.error("Failed to send message to channel {}: {}", channelId, e.getMessage());
            return null;
        });
    }


    private boolean isValidUrl(String text) {
        return text.matches("^(https?|ftp)://[^\\s/$.?#].[^\\s]*$");
    }
}