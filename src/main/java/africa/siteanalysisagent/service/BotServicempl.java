package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.dto.Button;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotServicempl implements BotService {

    private final TelexService telexService;
    private final MetaAnalysisService metaAnalysisService;
    private final Map<String, String> userUrls = new HashMap<>();
    private final Map<String, String> pendingOptimizations = new HashMap<>();
    private final Map<String, String> userStates = new HashMap<>();

    // Track last messages to prevent bot echoing
    private final Map<String, String> lastBotMessages = new ConcurrentHashMap<>();
    private final Map<String, String> lastUserMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Clean up old messages every hour
        cleanupExecutor.scheduleAtFixedRate(() -> {
            lastBotMessages.clear();
            lastUserMessages.clear();
            log.debug("Cleared message tracking caches");
        }, 1, 1, TimeUnit.HOURS);
    }

    @Override
    public void handleEvent(TelexUserRequest userRequest) {
        String text = userRequest.text();
        String channelId = userRequest.channelId();

        // Prevent processing bot-generated messages or repeated echoes
        if (shouldSkipMessage(text, channelId)) {
            return;
        }

        text = text.trim();
        lastUserMessages.put(channelId, text); // Track user message

        switch (text.toLowerCase()) {
            case "start" -> sendBotMessage(channelId, "\uD83D\uDC4B Hello! Would you like to scan a URL?\n\uD83D\uDC49 Type 'yes' to continue or 'no' to cancel.");
            case "yes" -> sendBotMessage(channelId, "✅ Please enter the URL you want to scan.");
            case "no" -> sendBotMessage(channelId, "❌ Okay! Let me know if you need anything else.");
            case "cancel" -> {
                userUrls.remove(channelId);
                sendBotMessage(channelId, "🚫 URL entry canceled. Please enter a new URL.");
            }
            case "confirm" -> handleUrlConfirmation(channelId);
            case "apply_fixes", "ignore" -> handleFixConfirmation(channelId, text);
            default -> handleDefaultInput(channelId, text);
        }
    }

    private boolean shouldSkipMessage(String text, String channelId) {
        return text == null || text.isBlank() || text.equals(lastBotMessages.get(channelId)) || text.equals(lastUserMessages.get(channelId));
    }

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
        metaAnalysisService.generateSeoReport(urlToScan, scanId, channelId);
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

    private void handleFixConfirmation(String channelId, String userInput) {
        if (userInput.equalsIgnoreCase("apply_fixes")) {
            String optimizedMetags = metaAnalysisService.getOptimizedMetags(channelId);
            sendBotMessage(channelId, "🤖 **Optimized Meta Tags:**\n" + optimizedMetags);
            userStates.remove(channelId);
        } else if (userInput.equalsIgnoreCase("ignore")) {
            sendBotMessage(channelId, "✅ AI fixes ignored. Let me know if you need further assistance.");
            metaAnalysisService.clearOptimizedMetags(channelId);
            userStates.remove(channelId);
        } else {
            sendBotMessage(channelId, "❌ Invalid input. Please type `apply_fixes` or `ignore`.");
        }
    }

    private void sendBotMessage(String channelId, String message) {
        lastBotMessages.put(channelId, message);
        telexService.sendMessage(channelId, message).exceptionally(e -> {
            log.error("Failed to send message to channel {}: {}", channelId, e.getMessage());
            return null;
        });
    }
    private void applyOptimizedMetaTags(String channelId) {
        if (pendingOptimizations.containsKey(channelId)) {
            String optimizedTags = pendingOptimizations.remove(channelId);
            telexService.sendMessage(channelId, "✅ AI-optimized meta tags have been applied successfully!\n\n" + optimizedTags);
        } else {
            telexService.sendMessage(channelId, "⚠️ No AI-optimized meta tags found! Please run a scan first.");
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