package tools.vitruv.methodologist.setup.vsum.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Configuration of the VSUM project generation and Maven build ({@code setup.vsum.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "setup.vsum")
public class VsumBuildProperties {

  /** Maven executable used to build the generated project. */
  private String mavenCommand = "mvn";

  /** Additional Maven arguments, e.g. {@code -s settings.xml}. */
  private List<String> mavenArguments = new ArrayList<>();

  /**
   * Local Maven repository of the builds; empty uses Maven's default ({@code ~/.m2/repository}).
   * The Docker image ships a repository with all dependencies of the generated projects.
   */
  private String mavenRepository = "";

  /** Build without network access, using only the local Maven repository. */
  private boolean offline = false;

  /**
   * Retry an offline build online when a dependency is missing from the local repository, e.g.
   * because a version was configured that the Docker image does not contain.
   */
  private boolean onlineFallback = true;

  /** How often a build that failed for infrastructure reasons is retried. */
  private int infrastructureRetries = 1;

  /** {@code MAVEN_OPTS} of the builds, e.g. the heap size; empty inherits the environment. */
  private String mavenOpts = "-Xmx1g";

  /** Minimum free space in the temporary directory needed to start a build. */
  private DataSize minFreeDiskSpace = DataSize.ofGigabytes(1);

  /** Workspaces left over from earlier runs are deleted on startup once they are this old. */
  private Duration workspaceRetention = Duration.ofDays(1);

  /** Warm-up build of a bundled sample project, see {@code VsumBuildWarmup}. */
  private Warmup warmup = new Warmup();

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

  /**
   * Version of VitruvOCL. It has no release yet, so a timestamped snapshot is pinned instead of
   * {@code 0.1.0-SNAPSHOT}, which changes whenever a new snapshot is published. Snapshots are
   * removed from the snapshot repository after a while; the Docker image keeps its copy.
   */
  private String vitruvoclVersion = "0.1.0-20261029.094026-91";

  /** Version of {@code org.eclipse.emf.common}. */
  private String emfCommonVersion = "2.46.0";

  /** Version of {@code org.eclipse.emf.ecore}. */
  private String emfEcoreVersion = "2.43.0";

  /** Version of JUnit Jupiter used by the generated smoke test. */
  private String junitVersion = "6.1.3";

  /** Version of the maven-assembly-plugin building the executable VSUM jar. */
  private String assemblyPluginVersion = "3.8.0";

  /** Configuration of the warm-up build. */
  @Getter
  @Setter
  public static class Warmup {

    /**
     * Build the sample project after startup; the service reports itself as not ready until the
     * build succeeded.
     */
    private boolean enabled = false;

    /**
     * Exit after the warm-up instead of serving requests, with exit code 0 if it succeeded. Used
     * while building the Docker image to fill its Maven repository.
     */
    private boolean exit = false;
  }
}
