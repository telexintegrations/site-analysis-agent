package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.Setting;
import africa.siteanalysisagent.model.TelexIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelexServiceIntegrationImpl implements TelexServiceIntegration {

    private final MetaAnalysisService metaAnalysisService;

    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://)[a-zA-Z0-9]+(.[a-zA-Z0-9]+)+(:[0-9]+)?(/[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=]*)?$");

    private static final String TELEX_CONFIG_JSON = """
            {
              "data": {
                "date": {
                  "created_at": "2025-03-12",
                  "updated_at": "2025-03-12"
                },
                "descriptions": {
                  "app_name": "Site Analysis Agent",
                  "app_description": "Site Analysis agent for Telex Integration: A tool that helps you analyze your website's SEO and meta tags.",
                  "app_logo": "https://lh3.googleusercontent.com/pw/AP1GczPfSJ0ewO2h17zvsr1EG3Kv_2I_Tl3Cgwb16VuYJ-eRo9sX9J7xXN4X0UpiEQsjTY_EpWH_-gjYaYdWO_JROaxEc-uxzuqCY9ZfM9yl2BzwwIoAicYNJROiI4KENYLy3V76X79ya6fEvrrxbmdAKmtS=w830-h828-s-no-gm?authuser=0",
                  "app_url": "https://site-analysis-agent.onrender.com/",
                  "background_color": "#fff"
                },
                "integration_category": "CRM & Customer Support",
                "integration_type": "modifier",
                "is_active": true,
                "key_features": [
                  "Single page meta analysis",
                  "Internal link crawling",
                  " Broken link detection",
                  " AI-powered meta suggestions"
                ],
                "author": "Telin",
                "settings": [
                    {
                        "label": "webhook_url",
                        "type": "text",
                        "description": "provide your telex channel webhook url",
                        "required": true,
                        "default": ""
                    }
                ],
                "target_url": "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/scrape",
                "tick_url": "https://site-analysis-agent.onrender.com/api/v1/meta-analysis/scrape"
                }
            }
            """;


    @Override
    public TelexIntegration getTelexConfig() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

// Force UTF-8
        String json = new String(TELEX_CONFIG_JSON.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        TelexIntegration telexIntegration = objectMapper.readValue(json, TelexIntegration.class);
        log.info("Telex Integration JSON Parsed Successfully on Render: {}", telexIntegration);
        return telexIntegration;
    }

    @Override
    public Map<String, Object> scrapeAndGenerateUrlReport(TelexUserRequest telexUserRequest) throws IOException {


        String userInput = sanitizeInput(telexUserRequest.message());

        if (!isValidUrl(userInput)) {
            throw new IllegalArgumentException("Invalid URL");
        }

        // Get Telex configuration
        TelexIntegration telexIntegration = getTelexConfig();

        // Extract webhook URL from settings
        List<Setting> settings = telexIntegration.data().settings();


        String webhookUrl = settings.stream()
                .filter(setting -> "webhook_url".equals(setting.label()))
                .map(Setting::settingDefault)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Webhook URL is missing from Telex settings"));

        try {


            Document document = metaAnalysisService.scrape(userInput);
            if (document == null) {
                throw new IllegalArgumentException("Invalid URL");
            }


            String seoReport = metaAnalysisService.generateSeoReport(userInput, settings);


            List<String> metaTagIssues = metaAnalysisService.checkMetaTags(document);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("url", userInput);
            responseData.put("seoReport", seoReport);
            responseData.put("metaTagIssues", metaTagIssues);
            return responseData;
        }catch (IllegalArgumentException exception){
            throw new IllegalArgumentException("Invalid URL");
        }
    }

    private static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("<[^>]*>", "").trim();
    }

    private static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return URL_PATTERN.matcher(url).matches();
    }
}
