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

@Service
@Slf4j
@RequiredArgsConstructor
public class BotServicempl implements BotService {

    private final TelexService telexService;
    private final MetaAnalysisService metaAnalysisService;
    private final Map<String, String> userUrls = new HashMap<>();
    private final Map<String, String> pendingOptimizations = new HashMap<>();
    private final Map<String, String> userStates = new HashMap<>(); // Tracks user states
    private final Map<String, String> lastSentMessage = new HashMap<>(); // Tracks last bot message per channel
    private final Map<String, String> lastBotMessagePerChannel = new ConcurrentHashMap<>();
    private final Map<String, String> lastUserMessagePerChannel = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();



    @PostConstruct
    public void init() {
        Timer timer = new Timer("MessageTrackerCleaner", true); // Daemon thread
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                lastBotMessagePerChannel.clear();
                lastUserMessagePerChannel.clear();
                log.debug("Cleared message tracking caches");
            }
        }, 300_000, 300_000); // Initial delay 5 min, repeat every 5 min
    }



    @Override
    public void handleEvent(TelexUserRequest userRequest) {
        String text = userRequest.text();
        String channelId = userRequest.channelId();

        // Skip processing if message is invalid or duplicate
        if (shouldSkipMessage(text, channelId)) {
            return;
        }

        // Track this user message
        lastUserMessagePerChannel.put(channelId, text);

        try {
            processUserMessage(text, channelId);
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
            sendTrackedMessage(channelId, "❌ An error occurred. Please try again.");
        }
    }

    private boolean shouldSkipMessage(String text, String channelId) {
        // Skip empty messages
        if (text == null || text.isBlank()) {
            log.debug("Skipping empty message");
            return true;
        }

        // Skip if this is the bot's own echoed message
        if (text.equals(lastBotMessagePerChannel.get(channelId))) {
            log.debug("Skipping bot echo: {}", text);
            return true;
        }

        // Skip duplicate user messages
        if (text.equals(lastUserMessagePerChannel.get(channelId))) {
            log.debug("Skipping duplicate user message: {}", text);
            return true;
        }

        return false;
    }

    private void processUserMessage(String text, String channelId) {
        text = text.trim();

        // Handle stateful interactions first
        if (handleStatefulInteraction(text, channelId)) {
            return;
        }
        // If the user is in "awaiting_fix_confirmation" state, handle fix confirmation
        if ("awaiting_fix_confirmation".equalsIgnoreCase(userStates.get(channelId))) {
            handleFixConfirmation(channelId, text);
            return;
        }

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

        if (text.equalsIgnoreCase("cancel")) {
            userUrls.remove(channelId);
            telexService.sendMessage(channelId, "🚫 URL entry canceled. Please enter a new URL.");
            return;
        }

        if (text.equalsIgnoreCase("confirm")) {
            if (!userUrls.containsKey(channelId)) {
                telexService.sendMessage(channelId, "⚠️ No URL found! Please enter a valid URL first.");
                return;
            }

            if (userStates.get(channelId) != null && userStates.get(channelId).equals("scanning")) {
                telexService.sendMessage(channelId, "⚠️ A scan is already in progress! Please wait...");
                return;
            }
            // Set state to "scanning"
            userStates.put(channelId, "scanning");

            String urlToScan = userUrls.get(channelId);
            telexService.sendMessage(channelId, "🔍 Scanning: " + urlToScan + "...\n⏳ Please wait...");

            // Perform the scan
            String scanId = UUID.randomUUID().toString();
            metaAnalysisService.generateSeoReport(urlToScan, scanId, channelId);

            // Notify the user that the scan is complete

            // Store AI optimizations for potential application//            telexService.sendMessage(channelId, "✅ Scan complete! Here's your report:\n\n" + seoReport);
//            pendingOptimizations.put(channelId, seoReport);

            // Set user state to wait for fix confirmation
            userStates.put(channelId, "awaiting_fix_confirmation");
            // Clear the URL state after the scan
            userUrls.remove(channelId);
            return;
        }

        if (text.equalsIgnoreCase("apply_fixes")) {
            handleFixConfirmation(channelId, text);
            return;
        }

        if (text.equalsIgnoreCase("ignore")) {
            handleFixConfirmation(channelId, text);
            return;
        }

        if (isValidUrl(text)) {
            userUrls.put(channelId, text);
            userStates.put(channelId, "waiting_for_confirmation");
            telexService.sendMessage(channelId, "🔗 You entered: " + text + "\n👉 Type 'confirm' to start scanning or 'cancel' to enter a new URL.");
            return;
        }

        telexService.sendMessage(channelId, "❌ Invalid command or URL. Please type 'start' to begin.");
    }
    private void sendTrackedMessage(String channelId, String message) {
        lastBotMessagePerChannel.put(channelId, message);
        telexService.sendMessage(channelId, message)
                .exceptionally(e -> {
                    log.error("Failed to send message: {}", e.getMessage());
                    return null;
                });
    }

    private boolean handleStatefulInteraction(String text, String channelId) {
        if ("awaiting_fix_confirmation".equalsIgnoreCase(userStates.get(channelId))) {
            handleFixConfirmation(channelId, text);
            return true;
        }
        return false;
    }


    private void handleFixConfirmation(String channelId, String userInput) {
        if (userInput.equalsIgnoreCase("apply_fixes")) {
            // Retrieve the optimized meta tags for this channel
            String optimizedMetags = metaAnalysisService.getOptimizedMetags(channelId);
            log.info("Retrieved optimized meta tags for channel {}: {}", channelId, optimizedMetags);

            // Send the optimized meta tags to the user
            telexService.sendMessage(channelId, "🤖 **Optimized Meta Tags:**\n" + optimizedMetags);



            userStates.remove(channelId); // Reset user state
        } else if (userInput.equalsIgnoreCase("ignore")) {
            telexService.sendMessage(channelId, "✅ AI fixes ignored. Let me know if you need further assistance.");
            metaAnalysisService.clearOptimizedMetags(channelId); // Clear optimizations
            userStates.remove(channelId); // Reset user state
        } else {
            telexService.sendMessage(channelId, "❌ Invalid input. Please type `apply_fixes` or `ignore`.");
        }
    }

    private void applyOptimizedMetaTags(String channelId) {
        if (pendingOptimizations.containsKey(channelId)) {
            String optimizedTags = pendingOptimizations.remove(channelId);
            telexService.sendMessage(channelId, "✅ AI-optimized meta tags have been applied successfully!\n\n" + optimizedTags);
        } else {
            telexService.sendMessage(channelId, "⚠️ No AI-optimized meta tags found! Please run a scan first.");
        }
    }

    private void sendFixPrompt(String channelId) {
        try {
//            telexService.sendInteractiveMessage(channelId,
//                    "📊 **SEO Analysis Complete!**\nWould you like to apply the AI-optimized fixes?\n👉 Type `apply_fixes` to apply or `ignore` to skip.",
//                    List.of(new Button("✅ Apply Fixes", "apply_fixes"), new Button("❌ Ignore", "ignore")));
        } catch (Exception e) {
            log.warn("⚠️ Failed to send interactive message. Sending plain text as fallback.");
            telexService.sendMessage(channelId, "Would you like to apply AI-optimized fixes? Type `apply_fixes` to apply or `ignore` to skip.");
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