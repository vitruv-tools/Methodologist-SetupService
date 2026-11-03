package tools.vitruv.methodologist.setup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Spring Boot application that sets up VSUMs and GenModels for the Methodologist. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MethodologistSetupServiceApplication {

  /**
   * Starts the service.
   *
   * @param args command line arguments, passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(MethodologistSetupServiceApplication.class, args);
  }
}
