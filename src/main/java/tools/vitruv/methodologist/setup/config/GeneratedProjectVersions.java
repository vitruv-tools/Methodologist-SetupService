package tools.vitruv.methodologist.setup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Versions of the Vitruvius artifacts used by generated VSUM projects, configurable via {@code
 * vsum.generator.versions.*}. The framework must provide the constraint SPI ({@code
 * tools.vitruv.framework.vsum.constraints}), which is available from 4.0.2.
 */
@Data
@Component
@ConfigurationProperties(prefix = "vsum.generator.versions")
public class GeneratedProjectVersions {
  /** Version of the {@code tools.vitruv:parent} POM. */
  private String parent = "4.0.0";

  /** Version of the Vitruv-Change artifacts ({@code tools.vitruv.change.*}). */
  private String change = "4.0.1";

  /** Version of the Vitruv framework artifacts ({@code tools.vitruv.framework.*}). */
  private String framework = "4.0.2-SNAPSHOT";

  /** Version of the Reactions language and runtime ({@code tools.vitruv.dsls.*}). */
  private String dsls = "4.0.0";

  /** Version of the Vitruv server ({@code tools.vitruv.server.remote}). */
  private String server = "4.0.1-SNAPSHOT";

  /** Version of VitruvOCL ({@code tools.vitruv.dsls:vitruvocl-language}). */
  private String vitruvocl = "0.1.0-SNAPSHOT";
}
