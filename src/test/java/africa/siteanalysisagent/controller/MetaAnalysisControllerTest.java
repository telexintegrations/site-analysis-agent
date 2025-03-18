package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.Data;
import africa.siteanalysisagent.model.Descriptions;
import africa.siteanalysisagent.model.TelexIntegration;
import africa.siteanalysisagent.service.TelexServiceIntegration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(controllers = MetaAnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class MetaAnalysisControllerTest {

    private static final Logger log = LoggerFactory.getLogger(MetaAnalysisControllerTest.class);
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TelexServiceIntegration telexServiceIntegration;

    private TelexUserRequest telexUserRequest;
    private Map<String, Object> scrapeResponse;
    private TelexIntegration telexIntegration;

    @BeforeEach
    void setUp() throws IOException {
        Descriptions descriptions = new Descriptions(
                "Test App",               // app_name
                "Short description",      // short_description
                "Long description",       // long_description
                "1.0.0",                  // version
                "John Doe"                // author
        );

        // ✅ Fix: Provide required arguments for TelexUserRequest
        telexUserRequest = new TelexUserRequest(
                "Analyze this URL",
                List.of() // Assuming settings are empty for now
        );

        // ✅ Mock scraping response
        scrapeResponse = Map.of(
                "metaDescription", "Example website",
                "title", "Example"
        );

        // ✅ Properly initialize Data
        Data telexData = new Data(
                new Data.DateInfo("2025-03-15", "2025-03-15"), // DateInfo
                descriptions, // ✅ Fixed: Pass the initialized Descriptions object
                true, // is_active
                "some_type", // integration_type
                "some_category", // integration_category
                List.of("Feature1", "Feature2"), // key_features
                "John Doe", // author
                List.of(), // ✅ settings (empty list instead of null)
                "https://api.telex.com", // target_url
                "https://api.telex.com/tick" // tick_url
        );

        telexIntegration = new TelexIntegration(telexData);

        // ✅ Mock service responses
        given(telexServiceIntegration.scrapeAndGenerateUrlReport(ArgumentMatchers.any()))
                .willReturn(scrapeResponse);

        given(telexServiceIntegration.getTelexConfig())
                .willReturn(telexIntegration);
    }

    @Test
    void scrapeAndGenerateUrlReport_Success() throws Exception {
        ResultActions response = mockMvc.perform(post("/api/v1/meta-analysis/scrape")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(telexUserRequest)));

        response.andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.metaDescription", CoreMatchers.is(scrapeResponse.get("metaDescription"))))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.title", CoreMatchers.is(scrapeResponse.get("title"))));
    }

    @Test
    void getTelexConfiguration_Failure() throws Exception {
        given(telexServiceIntegration.getTelexConfig())
                .willThrow(new RuntimeException("Failed to fetch configuration"));

        ResultActions response = mockMvc.perform(get("/api/v1/meta-analysis/telex"));

        response.andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", CoreMatchers.is("Failed to retrieve Telex configuration")));
    }

}