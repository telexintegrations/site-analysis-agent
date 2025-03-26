package africa.siteanalysisagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProgressTrackerImpl implements ProgressTracker {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private TelexService telexService;


    @Override
    public void sendProgress(String scanId, String channelId, int progress, String message) {
        try {

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scanId", scanId);
            payload.put("progress", progress);
            payload.put("message", message);

            System.out.println("Sending WebSocket update: " + payload);


            messagingTemplate.convertAndSend("/topic/progress/" + scanId, payload);

            String telexMessage = "🔍 **Scanning Progress**\n"
                    + "------------------------------\n"
                    + "📊 **Progress:** `" + progress + "%`\n"
                    + "📢 **Status:** `" + message + "`\n"
                    + "------------------------------";

            // Log Telex updates for debugging
            System.out.println("📩 Sending Telex update: " + telexMessage);

            telexService.sendMessage(channelId, telexMessage);
            Thread.sleep(500);
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    @Override
    public void sendReport(String scanId, String channelId, String title, String reportContent) {

        if (channelId == null || channelId.isEmpty()) {
            System.err.println("❌ Cannot send Telex report: channel_id is missing.");
            return;
        }

        String formattedReport = "**" + title + "**\n\n" + reportContent;

        System.out.println("📩 Sending Telex Report: " + title);
        telexService.sendMessage(channelId, formattedReport);
    }

    private String formatProgressMessage(int progress, String message) {
        return "🔍 **Scanning Progress**\n"
                + "------------------------------\n"
                + "📊 **Progress:** `" + progress + "%`\n"
                + "📢 **Status:** `" + message + "`\n"
                + "------------------------------";
    }


    @Override
    public void sendAlert(String channelId, String alertMessage) {

        if (channelId == null || channelId.isEmpty()) {
            System.err.println("❌ Cannot send alert: channel_id is missing.");
            return;
        }

        System.out.println("🚨 Sending Telex Alert: " + alertMessage);
        telexService.sendMessage(channelId, alertMessage);
    }
}
