package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Button;
import africa.siteanalysisagent.model.ChatResponse;
import africa.siteanalysisagent.model.ScheduleScanEvent;
import africa.siteanalysisagent.model.ScheduledScan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

        private final TelexService telexService;
        private final ApplicationEventPublisher applicationEventPublisher;

        private final Map<String, ScheduledScan> scheduledScans = new ConcurrentHashMap<>();
        private final Set<Integer> defaultIntervals = Set.of(1, 3, 6, 12, 24);


        //method for default interval
        public boolean scheduleDefaultInterval(String userId, String url, Long hours){
            if(!defaultIntervals.contains(hours)){
                return false;
            }
            return scheduleScan(userId, url, hours, TimeUnit.HOURS);
        }

        // method for custom intervals
       public boolean scheduleScan(String userId, String url, Long value, TimeUnit unit){
            if(value <= 0){
                return false;
            }

           // Validate minimum interval (e.g., at least 10 seconds)
           if(unit.toMillis(value) < 10000) {
               return false;
           }

           ScheduledScan scan = new ScheduledScan(userId, url, value, unit);
           scheduledScans.put(scan.getScanId(), scan);
           log.info("Scheduled scan added: {}", scan);
           return true;
       }

        // ScheduleManager methods
        public ChatResponse handleScheduleRequest(String userId, String message, String url) {

            // first try to parse the default interval
            Long defaultHours = Long.valueOf(parseDefaultInterval(message));
            if(defaultHours != null){
                boolean scheduled = scheduleDefaultInterval(userId, url, defaultHours);
                return scheduled ?
                        ChatResponse.success("✅ Scheduled scan for " + url + " every " + defaultHours + " hours") :
                        ChatResponse.error("Invalid interval. Use Every 1 hour, Every 3 hours, Every 6 hours, Every 12 hours, or Every 24 hours.");
            }

            //then custom interval
            ScheduleRequest customRequest = parseCustomInterval(message);
            if(customRequest != null){
                boolean scheduled = scheduleScan(userId, url, customRequest.value(), customRequest.unit());
                return scheduled ?
                        ChatResponse.success(createSuccessMessage(url, customRequest)) :
                        ChatResponse.error("Invalid custom interval");

            }
            // if neither show options
            return showIntervalOptions(url);

        }


    private Integer parseDefaultInterval(String message) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*hours?");
        Matcher matcher = pattern.matcher(message.toLowerCase());
        if (matcher.find()) {
            int hours = Integer.parseInt(matcher.group(1));
            return defaultIntervals.contains(hours) ? hours : null;
        }
        return null;
    }

    ScheduleRequest parseCustomInterval(String message) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*(second|minute|hour|day)s?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unitStr = matcher.group(2).toLowerCase();

            TimeUnit unit = switch (unitStr) {
                case "second" -> TimeUnit.SECONDS;
                case "minute" -> TimeUnit.MINUTES;
                case "hour" -> TimeUnit.HOURS;
                case "day" -> TimeUnit.DAYS;
                default -> null;
            };

            return unit != null ? new ScheduleRequest(value, unit) : null;
        }
        return null;
    }

    private String createSuccessMessage(String url, ScheduleRequest request) {
        String unitStr = request.unit().toString().toLowerCase();
        if (request.value() == 1) unitStr = unitStr.substring(0, unitStr.length() - 1); // singular
        return String.format("✅ Scheduled scan for %s every %d %s",
                url, request.value(), unitStr);
    }

    ChatResponse showIntervalOptions(String url) {
        List<Button> buttons = new ArrayList<>();

        // Add default interval buttons
        defaultIntervals.forEach(hours -> {
            buttons.add(new Button(
                    hours + "h",
                    hours + " hours",
                    "schedule:" + url + ":" + hours + ":hours"
            ));
        });

        // Add custom option
        buttons.add(new Button(
                "Custom",
                "custom",
                "schedule_custom:" + url
        ));

        return ChatResponse.prompt("Select scan interval for " + url + ":")
                .withButtons(buttons);
    }



    public ChatResponse listSchedules(String userId) {
            List<ScheduledScan> scans = getUserScans(userId);
            if (scans.isEmpty()) {
                return ChatResponse.info("You have no active scheduled scans.");
            }

            StringBuilder message = new StringBuilder("📅 Your Scheduled Scans:\n\n");
            scans.forEach(scan -> message.append(formatScan(scan)).append("\n"));
            message.append("\nUse 'cancel scan for [URL]' to remove a schedule.");

            return ChatResponse.info(message.toString())
                    .withButtons(getListButtons());
        }

        public ChatResponse cancelSchedule(String userId, String message) {
            String url = extractUrl(message);
            if (url == null) {
                return showCancelOptions(userId);
            }

            if (message.contains("all")) {
                cancelAllScans(userId);
                return ChatResponse.success("All your scheduled scans have been cancelled.");
            }

            boolean cancelled = cancelScan(userId, url);
            return cancelled ?
                    ChatResponse.success("Cancelled scheduled scans for " + url) :
                    ChatResponse.error("No active schedule found for " + url);
        }

        // ScheduleService methods
        public boolean scheduleScan(String userId, String url, Long intervalHours) {
            if (!defaultIntervals.contains(intervalHours)) {
                return false;
            }

            ScheduledScan scan = new ScheduledScan(userId, url, intervalHours, TimeUnit.HOURS);
            scheduledScans.put(scan.getScanId(), scan);
            log.info("Scheduled scan added: {}", scan);
            return true;
        }

        public boolean cancelScan(String userId, String url) {
            String scanId = userId + ":" + url;
            return scheduledScans.remove(scanId) != null;
        }

        public void cancelAllScans(String userId) {
            scheduledScans.entrySet().removeIf(entry ->
                    entry.getValue().getUserId().equals(userId)
            );
        }

        public List<ScheduledScan> getUserScans(String userId) {
            return scheduledScans.values().stream()
                    .filter(scan -> scan.getUserId().equals(userId))
                    .collect(Collectors.toList());
        }

    public ChatResponse handleCustomScheduleRequest(String userId, String intervalInput, String url) {
        // Parse custom interval (e.g., "30 minutes", "2 hours", "1 day")
        Pattern pattern = Pattern.compile("(\\d+)\\s*(second|minute|hour|day)s?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(intervalInput);

        if (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unitStr = matcher.group(2).toLowerCase();

            TimeUnit unit = switch (unitStr) {
                case "second" -> TimeUnit.SECONDS;
                case "minute" -> TimeUnit.MINUTES;
                case "hour" -> TimeUnit.HOURS;
                case "day" -> TimeUnit.DAYS;
                default -> null;
            };

            if (unit != null && scheduleScan(userId, url, value, unit)) {
                String unitName = value == 1 ? unitStr : unitStr + "s";
                return ChatResponse.success("✅ Scheduled scan for " + url + " every " + value + " " + unitName);
            }
        }

        return ChatResponse.error("""
        ❌ Invalid interval format
        Please use formats like:
        • "30 minutes"
        • "2 hours"
        • "1 day"
        """);
    }

        @Scheduled(fixedRate = 3600000) // Run every hour
        public void executeDueScans() {
            LocalDateTime now = LocalDateTime.now();

            scheduledScans.values().stream()
                    .filter(ScheduledScan::isActive)
                    .filter(scan -> isScanDue(scan, now))
                    .forEach(this::executeScan);
        }

        // Private helper methods
        ChatResponse createSchedule(String userId, String url, Long interval) {
            boolean success = scheduleScan(userId, url, interval, TimeUnit.HOURS);
            if (success) {
                return ChatResponse.success(
                        String.format("✅ Scheduled scan for %s every %d hours.", url, interval)
                ).withButtons(getScheduleButtons(url));
            }
            return ChatResponse.error("Invalid interval. Use Every 1 hour, Every 3 hours, Every 6 hours, Every 12 hours, or Every 24 hours.");
        }

        private boolean isScanDue(ScheduledScan scan, LocalDateTime now) {
            return scan.getLastScanTime().plusHours(scan.getIntervalValue()).isBefore(now);
        }

        private void executeScan(ScheduledScan scan) {
            try {
                log.info("Executing scheduled scan for {}: {}", scan.getUserId(), scan.getUrl());
                scan.setLastScanTime(LocalDateTime.now());
                applicationEventPublisher.publishEvent(new ScheduleScanEvent(scan.getUserId(),scan.getUrl()));
            } catch (Exception e) {
                log.error("Failed to execute scheduled scan {}", scan.getScanId(), e);
                sendScanErrorNotification(scan, e);
            }
        }

        private void sendScanReport(ScheduledScan scan, String report) {
            String message = String.format("""
            ⏰ *Scheduled Scan Report*
            ━━━━━━━━━━━━━━━
            • URL: %s
            • Interval: Every %d hours
            • Last Scan: %s
                        
            %s
            """,
                    scan.getUrl(),
                    scan.getIntervalValue(),
                    scan.getLastScanTime(),
                    report
            );

            telexService.sendMessage(scan.getUserId(), message);
        }

        private String formatScan(ScheduledScan scan) {
            return String.format("• %s - Every %d hours (Last: %s)",
                    scan.getUrl(),
                    scan.getIntervalValue(),
                    scan.getLastScanTime()
            );
        }

        private List<Button> getScheduleButtons(String url) {
            return List.of(
                    new Button("Run Now", "run_now", "schedule_run_now:" + url),
                    new Button("Cancel", "cancel", "schedule_cancel:" + url)
            );
        }

        private List<Button> getListButtons() {
            return List.of(
                    new Button("Refresh", "refresh", "list_schedules"),
                    new Button("Cancel All", "cancel_all", "schedule_cancel_all"),
                    new Button("New Schedule", "new_schedule", "show_schedule_options")
            );
        }

        

        private ChatResponse showCancelOptions(String userId) {
            List<ScheduledScan> scans = getUserScans(userId);
            if (scans.isEmpty()) {
                return ChatResponse.info("No schedules to cancel.");
            }

            return ChatResponse.prompt("Which schedule would you like to cancel?")
                    .withButtons(scans.stream()
                            .map(scan -> new Button(
                                    "Cancel " + scan.getUrl(),
                                    "cancel_" + scan.getUrl(),
                                    "schedule_cancel:" + scan.getUrl()
                            ))
                            .collect(Collectors.toList()));
        }

        private Integer extractIntervalFromMessage(String message) {
            Pattern pattern = Pattern.compile("(\\d+)\\s*(hour|hr|hours|hrs)");
            Matcher matcher = pattern.matcher(message.toLowerCase());

            if (matcher.find()) {
                int interval = Integer.parseInt(matcher.group(1));
                if (List.of(1, 3, 6, 12, 24).contains(interval)) {
                    return interval;
                }
            }
            return null;
        }

        private String extractUrl(String message) {
            Pattern urlPattern = Pattern.compile("(https?://\\S+|www\\.\\S+\\.\\S+)");
            Matcher matcher = urlPattern.matcher(message);
            return matcher.find() ? matcher.group(1) : null;
        }

    private void sendScanErrorNotification(ScheduledScan scan, Exception error) {
        try {
            String errorDetails = buildErrorDetails(error);
            String message = String.format("""
            ‼️ *Scheduled Scan Failed*
            ━━━━━━━━━━━━━━━
            • URL: %s
            • User: %s
            • Time: %s
            • Interval: Every %d hours
            • Error Type: %s
            • Error Details: %s
            ━━━━━━━━━━━━━━━
            """,
                    scan.getUrl(),
                    scan.getUserId(),
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    scan.getIntervalValue(),
                    error.getClass().getSimpleName(),
                    errorDetails
            );

            // Add additional context for debugging
            log.error("Scheduled scan failed for user {} (URL: {}). Error: {}",
                    scan.getUserId(), scan.getUrl(), errorDetails, error);

            // Send via Telex with error handling
            telexService.sendMessage(scan.getUserId(), message)
                    .exceptionally(telexError -> {
                        log.error("Failed to send error notification to Telex for user {}",
                                scan.getUserId(), telexError);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Failed to generate error notification for scheduled scan failure", e);
        }
    }

    private String buildErrorDetails(Throwable error) {
        StringBuilder details = new StringBuilder();

        // Include root cause message
        Throwable rootCause = getRootCause(error);
        details.append(rootCause.getMessage());

        // For production, you might want to limit stack trace details
        if (log.isDebugEnabled()) {
            details.append("\n\nStack Trace:\n");
            for (StackTraceElement element : rootCause.getStackTrace()) {
                if (element.getClassName().startsWith("africa.siteanalysisagent")) {
                    details.append(element).append("\n");
                }
            }
        }

        return details.toString().trim();
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    public record ScheduleRequest(long value, TimeUnit unit) {}
}



