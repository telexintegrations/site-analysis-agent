package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.Data;
import africa.siteanalysisagent.model.Descriptions;
import africa.siteanalysisagent.model.TelexIntegration;
import africa.siteanalysisagent.service.TelexServiceIntegration;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                "Test App",
                "Short description",
                "Long description",
                "1.0.0",
                "John Doe"
        );

        telexUserRequest = new TelexUserRequest(
                "Analyze this URL",
                List.of()
        );

        scrapeResponse = Map.of(
                "metaDescription", "Example website",
                "title", "Example"
        );

        Data telexData = new Data(
                new Data.DateInfo("2025-03-15", "2025-03-15"),
                descriptions,
                true,
                "some_type",
                "some_category",
                List.of("Feature1", "Feature2"),
                "John Doe",
                List.of(),
                "https://api.telex.com",
                "https://api.telex.com/tick"
        );

        telexIntegration = new TelexIntegration(telexData);

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
    void getTelexConfiguration_Success() throws Exception {
        given(telexServiceIntegration.getTelexConfig())
                .willReturn(telexIntegration);

        ResultActions response = mockMvc.perform(get("/api/v1/meta-analysis/telex"));

        response.andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.target_url", CoreMatchers.is(telexIntegration.data().target_url())));
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