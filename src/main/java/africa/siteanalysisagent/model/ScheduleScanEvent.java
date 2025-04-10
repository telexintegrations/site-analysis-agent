package africa.siteanalysisagent.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ScheduleScanEvent {
    private final String userId;
    private final String url;
}
