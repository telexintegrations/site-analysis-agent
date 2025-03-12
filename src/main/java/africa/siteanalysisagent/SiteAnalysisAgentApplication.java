package africa.siteanalysisagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;

@SpringBootApplication
@OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "Site Analysis Agent Documentation", version = "1.0", description = "Site Analysis agent for Telex Integration documentation"))
public class SiteAnalysisAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiteAnalysisAgentApplication.class, args);
    }

}
