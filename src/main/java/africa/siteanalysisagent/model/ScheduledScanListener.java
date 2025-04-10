package africa.siteanalysisagent.model;

import africa.siteanalysisagent.service.LynxService;
import africa.siteanalysisagent.service.TelexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduledScanListener {
    private final LynxService lynxService;
    private final TelexService telexService;


    @EventListener
    @Async
    public void handleScheduledScan(ScheduleScanEvent event){
        try {
            log.info("Executing scheduled scan for user {} on URL {}",
                    event.getUserId(), event.getUrl());

            ChatResponse response =lynxService.processAnalysisRequest(
                    event.getUserId(),
                    "Scheduled scan",
                    event.getUrl()
            );

            telexService.sendMessage(event.getUserId(), "Scheduled scan completed for " + event.getUrl());
        }catch (Exception e){
            log.error("Failed to process scheduled scan", e);
            telexService.sendMessage(event.getUserId(),
                    "Failed to process scheduled scan for " + event.getUrl());
        }
    }
}
