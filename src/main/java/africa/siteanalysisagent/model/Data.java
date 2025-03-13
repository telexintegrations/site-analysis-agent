package africa.siteanalysisagent.model;

import java.util.List;

public record Data(
        DateInfo date,
        Descriptions descriptions,
        boolean is_active,
        String integration_type,
        String integration_category,
        List<String> key_features,
        String author,
        List<Setting> settings,
        String target_url,
        String tick_url) {

    public record DateInfo(
            String created_at,
            String updated_at
    ) {}
}
