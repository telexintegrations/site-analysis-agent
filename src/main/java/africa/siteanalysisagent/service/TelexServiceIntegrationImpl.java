package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.Setting;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelexServiceIntegrationImpl implements TelexServiceIntegration {

    private final MetaAnalysisService metaAnalysisService;

    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://)[a-zA-Z0-9]+(.[a-zA-Z0-9]+)+(:[0-9]+)?(/[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=]*)?$");

    private static final String TELEX_CONFIG_JSON = "integration.json";

    @Override
    public TelexIntegration getTelexConfig() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);

        // Parse Telex settings JSON
        TelexIntegration telexIntegration = objectMapper.readValue(TELEX_CONFIG_JSON, TelexIntegration.class);

        return telexIntegration;
    }

    @Override
    public Map<String, Object> scrapeAndGenerateUrlReport(TelexUserRequest telexUserRequest) throws IOException {


        String userInput = sanitizeInput(telexUserRequest.message());

        if (!isValidUrl(userInput)) {
            throw new IllegalArgumentException("Invalid URL");
        }

        // Log the settings for debugging
        log.info("Settings received: {}", telexUserRequest.settings());


        String webhookUrl = telexUserRequest.settings().stream()
                .filter(setting -> "webhook_url".equals(setting.label()))
                .map(Setting::defaultValue)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Webhook URL is missing from Telex settings"));

        log.info("Webhook URL retrieved: {}", webhookUrl);

        try {


            Document document = metaAnalysisService.scrape(userInput);
            if (document == null) {
                throw new IllegalArgumentException("Invalid URL");
            }


            String seoReport = metaAnalysisService.generateSeoReport(userInput, webhookUrl);


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
