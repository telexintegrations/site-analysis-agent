package africa.siteanalysisagent.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisRequest {
    private String event_name;
    private String message;
    private String status;
    private String username;
}
