package africa.siteanalysisagent.model;

import africa.siteanalysisagent.dto.CategorizedLink;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SEOReport {
    private String url;
    private Map<String, List<String>> metaTags;
    private CategorizedLink categorizedLinks;
    private int score;
    private String recommendations;
    private String optimizedMetaTags;
    private String rawHtml;
}
