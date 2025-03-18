package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntegration;
import africa.siteanalysisagent.service.TelexServiceIntegration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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


    @Test
    void scrapeAndGenerateUrlReport_Success() throws Exception {
        Map<String, Object> dummyResponse = Map.of(
                "url", "https://example.com/page",
                "seoReport", "Dummy SEO Report",
                "metaTagIssues", List.of("Issue1", "Issue2")
        );
        given(telexServiceIntegration.scrapeAndGenerateUrlReport(ArgumentMatchers.any()))
                .willReturn(dummyResponse);

        TelexUserRequest request = new TelexUserRequest("https://example.com/page", List.of());

        mockMvc.perform(post("/api/v1/meta-analysis/scrape")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", CoreMatchers.is(HttpStatus.OK.value())))
                .andExpect(jsonPath("$.message", CoreMatchers.is("scrape successful")))
                .andExpect(jsonPath("$.data.url", CoreMatchers.is("https://example.com/page")))
                .andExpect(jsonPath("$.data.seoReport", CoreMatchers.is("Dummy SEO Report")))
                .andExpect(jsonPath("$.data.metaTagIssues[0]", CoreMatchers.is("Issue1")))
                .andExpect(jsonPath("$.data.metaTagIssues[1]", CoreMatchers.is("Issue2")));
    }

    @Test
    void getTelexConfiguration_Success() throws Exception {
        TelexIntegration dummyIntegration = new TelexIntegration(null);
        given(telexServiceIntegration.getTelexConfig()).willReturn(dummyIntegration);

        mockMvc.perform(get("/api/v1/meta-analysis/telex"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(dummyIntegration)));
    }

    @Test
    void getTelexConfiguration_Failure() throws Exception {
        given(telexServiceIntegration.getTelexConfig())
                .willThrow(new RuntimeException("Test failure"));

        mockMvc.perform(get("/api/v1/meta-analysis/telex"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", CoreMatchers.is("Failed to retrieve Telex configuration")))
                .andExpect(jsonPath("$.error", CoreMatchers.is("Test failure")))
                .andExpect(jsonPath("$.status", CoreMatchers.is(HttpStatus.INTERNAL_SERVER_ERROR.value())))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}