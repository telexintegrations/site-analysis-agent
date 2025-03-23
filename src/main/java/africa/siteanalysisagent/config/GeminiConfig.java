package africa.siteanalysisagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GptConfig {

    @Value("${gpt.api.key}")
    private String apiKey;

    @Bean
    public String gptApiKey() {
        return apiKey;
    }
}
