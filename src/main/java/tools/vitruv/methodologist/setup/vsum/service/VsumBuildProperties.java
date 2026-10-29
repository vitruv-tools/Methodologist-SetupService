package tools.vitruv.methodologist.setup.vsum.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration of the VSUM project generation and Maven build ({@code setup.vsum.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "setup.vsum")
public class VsumBuildProperties {

  /** Maven executable used to build the generated project. */
  private String mavenCommand = "mvn";

  /** Additional Maven arguments, e.g. {@code -o} or {@code -s settings.xml}. */
  private List<String> mavenArguments = new ArrayList<>();

  /** Maximum duration of a single Maven build. */
  private Duration buildTimeout = Duration.ofMinutes(15);

  /** Maximum number of Maven builds running at the same time; further requests wait. */
  private int maxConcurrentBuilds = 2;

  /** Number of trailing Maven output lines included in error responses. */
  private int errorOutputLines = 60;

  /** Keep the workspace of failed builds for debugging instead of deleting it. */
  private boolean keepFailedWorkspaces = false;

  /** Version of the {@code tools.vitruv:parent} build parent. */
  private String parentVersion = "4.0.0";

  // Vitruvius is released per repository, so the artifact groups can have different versions.

  /** Version of the Vitruv-Change artifacts ({@code tools.vitruv.change.*}). */
  private String vitruvChangeVersion = "4.0.1";

  /** Version of the Vitruv framework artifacts ({@code tools.vitruv.framework.*}). */
  private String vitruvFrameworkVersion = "4.0.1";

  /** Version of the Vitruv-DSLs artifacts ({@code tools.vitruv.dsls.*}, e.g. Reactions). */
  private String vitruvDslsVersion = "4.0.0";

  /** Version of the Vitruv-Server artifacts ({@code tools.vitruv.server.*}). */
  private String vitruvServerVersion = "4.0.0";

  /** Version of VitruvOCL. */
  private String vitruvoclVersion = "0.1.0-SNAPSHOT";

  /** Version of {@code org.eclipse.emf.common}. */
  private String emfCommonVersion = "2.46.0";

  /** Version of {@code org.eclipse.emf.ecore}. */
  private String emfEcoreVersion = "2.43.0";

  /** Version of JUnit Jupiter used by the generated smoke test. */
  private String junitVersion = "6.1.3";

  /** Version of the maven-assembly-plugin building the executable VSUM jar. */
  private String assemblyPluginVersion = "3.8.0";
}
