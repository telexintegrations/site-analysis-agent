package africa.siteanalysisagent.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Data
public class ScheduledScan {
    private String scanId;
    private String userId;
    private String url;
    private Long intervalValue;
    private java.util.concurrent.TimeUnit timeUnit;
    private LocalDateTime lastScanTime;
    private boolean active = true;

    public enum TimeUnit {
        SECONDS {
            public long toMillis(long value) { return value * 1000; }
        },
        MINUTES {
            public long toMillis(long value) { return value * 60 * 1000; }
        },
        HOURS {
            public long toMillis(long value) { return value * 60 * 60 * 1000; }
        },
        DAYS {
            public long toMillis(long value) { return value * 24 * 60 * 60 * 1000; }
        };

        public abstract long toMillis(long value);
    }

    public ScheduledScan(String userId, String url, Long intervalValue, java.util.concurrent.TimeUnit timeUnit) {
        this.scanId = userId + ":" + url;
        this.userId = userId;
        this.url = url;
        this.intervalValue = intervalValue;
        this.timeUnit = timeUnit;
        this.lastScanTime = LocalDateTime.now();
    }

    public long getIntervalInMillis() {
        return timeUnit.toMillis(intervalValue);
    }
}
