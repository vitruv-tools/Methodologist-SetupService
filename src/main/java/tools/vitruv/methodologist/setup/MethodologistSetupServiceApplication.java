package tools.vitruv.methodologist.setup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MethodologistSetupServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MethodologistSetupServiceApplication.class, args);
  }
}
