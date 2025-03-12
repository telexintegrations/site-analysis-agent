package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.ApiErrorResponse;
import africa.siteanalysisagent.dto.UrlRequest;
import africa.siteanalysisagent.model.TelexIntergration;
import africa.siteanalysisagent.service.MetaAnalysisService;
import africa.siteanalysisagent.service.TelexService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/meta-analysis")
public class MetaAnalysisController {

    @Autowired
    private MetaAnalysisService metaAnalysisService;
    @Autowired
    private TelexService telexService;

    @PostMapping("/scrape")
    public String scrapeAndGenerateUrlReport(@RequestBody UrlRequest entity) {
        String url = entity.getUrl();

        try {

            Document document = null;
            document = metaAnalysisService.scrape(url);
            String seoReport = metaAnalysisService.generateSeoReport(url);
            if (document != null) {
                List<String> metaTagIssues = metaAnalysisService.checkMetaTags(document);

                if (metaTagIssues.size() > 0) {
                    System.out.println("Meta Tags Found: " + metaTagIssues);
                }

                telexService.notifyTelex(
                        "Site analysis for " +
                                url + "successful\n"
                                + "\nSEO Report:"
                                + "\n"
                                + seoReport);
//                                + "\nMeta Tag Issues:"
//                                + "\n" + metaTagIssues);
                return "Scraping successful";
            } else {
                return "Unable to scrape" + url;
            }

        } catch (Exception e) {
            return "Failed to scrape" + url + e.getLocalizedMessage();
        }
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/telex")
    public ResponseEntity<?> getMethodName() {
        System.out.println("called telex config file");
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
                      "app_url": "https://thetechhut.co/whatsapp/",
                      "background_color": "#fff"
                    },
                    "is_active": true,
                    "integration_type": "modifier",
                    "integration_category": "CRM & Customer Support",
                    "key_features": [
                      "Gemini API Powered",
                      "Auto reply to customers on Whatsapp in a minute",
                      "Automatically get notifications of customer messages sent to whatsapp"
                    ],
                    "author": "Telin Backend Devs",
                    "settings": [
                        {
                            "label": "Message",
                            "type": "text",
                            "required": true,
                            "default": "Hello, I am a bot. How can I help you today?"
                        },
                        {
                            "label": "Time interval",
                            "type": "text",
                            "required": true,
                            "default": "* * * * *"
                        }
                    ],
                    "target_url": "https://thisinternshiprocks-e8d4gycsc9gec0ht.southafricanorth-01.azurewebsites.net/reply-with-ai",
                    "tick_url": "https://thisinternshiprocks-e8d4gycsc9gec0ht.southafricanorth-01.azurewebsites.net/reply-with-ai-tick"
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
