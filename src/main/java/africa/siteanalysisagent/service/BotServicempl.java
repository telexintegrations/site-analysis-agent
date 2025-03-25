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

    private final Map<String, String> lastBotMessages = new ConcurrentHashMap<>();
    private final Map<String, String> lastUserMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    private static final String IDENTIFIER = "📝 #TelexSite"; // Identifier to detect bot-echoed messages


    @PostConstruct
    public void init() {
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

        if (shouldSkipMessage(text, channelId)) {
            return;
        }

        final String userText = text.trim();
        lastUserMessages.put(channelId, userText);

        asyncExecutor.submit(() -> processUserInput(channelId, userText));
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

    private boolean shouldSkipMessage(String text, String channelId) {
        if (text == null || text.isBlank()) return true;

        String normalizedText = text.toLowerCase().trim();

        // Ignore messages containing the identifier (which means Telex echoed them)
        if (normalizedText.contains(IDENTIFIER.toLowerCase())) {
            log.debug("Skipping Telex-echoed message in channel {}", channelId);
            return true;
        }
        return normalizedText.equals(lastBotMessages.get(channelId)) || normalizedText.equals(lastUserMessages.get(channelId));

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
        } else {
            sendBotMessage(channelId, "❌ Invalid input. Please type `apply_fixes` or `ignore`.");
        }
    }

    private void sendBotMessage(String channelId, String message) {
        lastBotMessages.put(channelId, message);

        telexService.sendMessage(channelId, message).thenApply(response -> {
            // When Telex sends back this message, **it should include the identifier**
            String taggedMessage = message + "\n\n" + IDENTIFIER;
            lastBotMessages.put(channelId, taggedMessage); // Track it so the bot ignores it later
            return null;
        }).exceptionally(e -> {
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