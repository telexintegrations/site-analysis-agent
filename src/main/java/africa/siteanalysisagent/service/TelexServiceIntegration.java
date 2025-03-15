package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntegration;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.Map;

public interface TelexServiceIntegration {
    TelexIntegration getTelexConfig() throws JsonProcessingException;
    Map<String,Object> scrapeAndGenerateUrlReport(TelexUserRequest telexUserRequest) throws IOException;
}
