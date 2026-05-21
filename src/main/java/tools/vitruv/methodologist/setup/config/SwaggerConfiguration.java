package tools.vitruv.methodologist.setup.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI configuration for API documentation.
 */
@Configuration
public class SwaggerConfiguration {

  /**
   * Configures OpenAPI documentation.
   *
   * @return OpenAPI object with API metadata
   */
  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Methodologist Setup Service API")
                .version("1.0.0")
                .description(
                    "REST API for validating and standardizing GenModel files "
                        + "for MWE2 workflow compatibility")
                .contact(
                    new Contact()
                        .name("Methodologist Team")
                        .email("info@methodologist.tools"))
                .license(new License().name("Apache 2.0")));
  }
}

