package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.TelexUserRequest;
import africa.siteanalysisagent.model.TelexIntergration;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.Map;

public interface TelexServiceIntegration {
    TelexIntergration getTelexConfig() throws JsonProcessingException;
    Map<String,Object> scrapeAndGenerateUrlReport(TelexUserRequest telexUserRequest) throws IOException;
}
