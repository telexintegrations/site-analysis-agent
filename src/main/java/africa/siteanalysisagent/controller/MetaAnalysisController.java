package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.ApiErrorResponse;
import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntergration;
import africa.siteanalysisagent.service.MetaAnalysisService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/meta-analysis")
@RequiredArgsConstructor
public class MetaAnalysisController {

    private final MetaAnalysisService metaAnalysisService;


    private static String removeHtmlTags(String input) {
        return input.replaceAll("</?\\w+[^>]*>", "");
    }

    @PostMapping("/scrape")
    public String scrapeAndGenerateUrlReport(@RequestBody TelexUserRequest entity) {

        Pattern regex = Pattern.compile("^(http|https)://.*$");
        String userInput = removeHtmlTags(entity.message());

        if (!regex.matcher(userInput).matches()) {
            return "Invalid URL";
        }

        String url = userInput;
        try {
            metaAnalysisService.generateSeoReport(url);
        } catch (Exception e) {
            return "Failed to scrape" + url + e.getLocalizedMessage();
        }
        return "scraping sent to telex";
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/telex")
    public ResponseEntity<?> getMethodName() {

        String json = """
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

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
            TelexIntergration telexIntergration = objectMapper.readValue(json,
                    TelexIntergration.class);
            return ResponseEntity.status(HttpStatus.OK).body(telexIntergration);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ApiErrorResponse(
                            "Localized Message" + e.getLocalizedMessage(),
                            "Error" + e.getMessage(),
                            HttpStatus.BAD_REQUEST.value(),
                            LocalDate.now().toString()));

        }

    }

}
