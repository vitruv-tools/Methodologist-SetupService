package tools.vitruv.methodologist.setup.vsum.service.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.VsumBuildProperties;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Runs {@code mvn clean verify} on a generated project. The build includes the generated smoke
 * test, so a successful build means the VSUM can actually be instantiated.
 *
 * <p>Failed builds are classified by their output (see {@link BuildFailure}): problems of the build
 * environment are retried and reported as such, so that they are not mistaken for problems of the
 * uploaded files.
 */
@Slf4j
@Service
public class MavenBuildRunner {

  private static final String BUILD_LOG = "build.log";
  private static final Pattern MODULE_START = Pattern.compile("^\\[INFO] Building (\\S+) ");

  private static final Map<String, String> MODULE_DESCRIPTIONS =
      Map.of(
          ProjectLayout.MODEL_ARTIFACT_ID, "the code of the metamodels",
          ProjectLayout.CONSISTENCY_ARTIFACT_ID, "the reactions",
          ProjectLayout.VSUM_ARTIFACT_ID, "the VSUM and its smoke test");

  private final VsumBuildProperties properties;
  private final Semaphore buildSlots;

  /**
   * Creates the runner.
   *
   * @param properties build configuration
   */
  public MavenBuildRunner(VsumBuildProperties properties) {
    this.properties = properties;
    this.buildSlots = new Semaphore(Math.max(1, properties.getMaxConcurrentBuilds()), true);
  }

  /**
   * Builds the project, waiting for a free build slot first. Builds that fail because of the build
   * environment are retried up to {@code setup.vsum.infrastructure-retries} times; an offline build
   * that misses a dependency is retried online if {@code setup.vsum.online-fallback} is set.
   *
   * @param projectRoot root of the generated project
   * @param logDirectory directory receiving the build logs; must be outside the project
   * @throws MethodologistSetupException when the build fails, times out, or cannot be started; the
   *     error code tells whether the uploaded files ({@link VsumErrorCodes#BUILD_FAILED}), the
   *     build environment ({@link VsumErrorCodes#BUILD_UNAVAILABLE}), or the service itself ({@link
   *     VsumErrorCodes#INTERNAL_ERROR}) is responsible
   */
  public void build(Path projectRoot, Path logDirectory) {
    try {
      buildSlots.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw interrupted(e);
    }
    try {
      buildWithRetries(projectRoot, logDirectory);
    } finally {
      buildSlots.release();
    }
  }

  private void buildWithRetries(Path projectRoot, Path logDirectory) {
    checkFreeDiskSpace(projectRoot);
    boolean offline = properties.isOffline();
    for (int attempt = 0; ; attempt++) {
      Path logFile =
          logDirectory.resolve(attempt == 0 ? BUILD_LOG : "build-retry-" + attempt + ".log");
      if (run(projectRoot, logFile, offline)) {
        return;
      }
      List<String> output = readLog(logFile);
      BuildFailure failure = BuildFailure.classify(output);
      boolean canRetry = attempt < properties.getInfrastructureRetries();
      if (failure == BuildFailure.MISSING_OFFLINE && properties.isOnlineFallback() && canRetry) {
        log.warn(
            "A dependency is missing from the local Maven repository; retrying the build online."
                + " Rebuild the Docker image to include it. Log: {}",
            logFile);
        offline = false;
      } else if (failure == BuildFailure.INFRASTRUCTURE && canRetry) {
        log.warn("VSUM build failed because of the build environment; retrying. Log: {}", logFile);
      } else {
        log.warn("VSUM build failed ({}); log: {}", failure, logFile);
        throw failed(failure, output);
      }
    }
  }

  /** Runs Maven once and returns whether it succeeded. */
  private boolean run(Path projectRoot, Path logFile, boolean offline) {
    List<String> command = command(offline);
    log.info("Building generated VSUM project in {}: {}", projectRoot, String.join(" ", command));
    ProcessBuilder processBuilder =
        new ProcessBuilder(command)
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
            .redirectOutput(logFile.toFile());
    // the service's own JVM options (e.g. MaxRAMPercentage in docker-compose.yaml) would apply to
    // every Maven and test JVM of every build; builds are configured by MAVEN_OPTS instead
    processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
    if (!properties.getMavenOpts().isBlank()) {
      processBuilder.environment().put("MAVEN_OPTS", properties.getMavenOpts());
    }

    Process process;
    try {
      process = processBuilder.start();
    } catch (IOException e) {
      throw new MethodologistSetupException(
          VsumErrorCodes.BUILD_ERROR,
          "Could not start Maven ('" + properties.getMavenCommand() + "'): " + e.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          e);
    }

    try {
      boolean finished =
          process.waitFor(properties.getBuildTimeout().toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        destroy(process);
        throw new MethodologistSetupException(
            VsumErrorCodes.BUILD_TIMEOUT,
            "The VSUM build did not finish within "
                + properties.getBuildTimeout()
                + runningModule(readLog(logFile)),
            HttpStatus.GATEWAY_TIMEOUT,
            null);
      }
    } catch (InterruptedException e) {
      destroy(process);
      Thread.currentThread().interrupt();
      throw interrupted(e);
    }

    if (process.exitValue() == 0) {
      log.info("VSUM build succeeded in {}", projectRoot);
      return true;
    }
    return false;
  }

  /**
   * The Maven command line.
   *
   * @param offline whether to build without network access
   * @return the command and its arguments
   */
  List<String> command(boolean offline) {
    List<String> command = new ArrayList<>();
    command.add(properties.getMavenCommand());
    command.addAll(List.of("--batch-mode", "--no-transfer-progress"));
    if (offline) {
      command.add("--offline");
    }
    if (!properties.getMavenRepository().isBlank()) {
      command.add("-Dmaven.repo.local=" + properties.getMavenRepository());
    }
    // concurrent builds share the local repository; lock it across processes (Maven 3.9+)
    command.add("-Daether.syncContext.named.factory=file-lock");
    command.add("-Daether.syncContext.named.nameMapper=file-gav");
    command.addAll(List.of("clean", "verify"));
    command.addAll(properties.getMavenArguments());
    return command;
  }

  private void checkFreeDiskSpace(Path projectRoot) {
    long required = properties.getMinFreeDiskSpace().toBytes();
    long free;
    try {
      free = Files.getFileStore(projectRoot).getUsableSpace();
    } catch (IOException e) {
      log.warn("Could not determine the free disk space: {}", e.getMessage());
      return;
    }
    if (free < required) {
      throw new MethodologistSetupException(
          VsumErrorCodes.BUILD_UNAVAILABLE,
          String.format(
              "The build server is low on disk space (%d MB free, %d MB needed); try again later",
              free / (1024 * 1024), required / (1024 * 1024)),
          HttpStatus.SERVICE_UNAVAILABLE,
          null);
    }
  }

  private MethodologistSetupException failed(BuildFailure failure, List<String> output) {
    String summary = summarize(output);
    return switch (failure) {
      case MISSING_OFFLINE ->
          new MethodologistSetupException(
              VsumErrorCodes.BUILD_UNAVAILABLE,
              "The VSUM could not be built because a dependency is missing from the build"
                  + " server's Maven repository. This is not a problem with the uploaded files;"
                  + " the setup service must be redeployed with the configured versions:\n"
                  + summary,
              HttpStatus.SERVICE_UNAVAILABLE,
              null);
      case INFRASTRUCTURE ->
          new MethodologistSetupException(
              VsumErrorCodes.BUILD_UNAVAILABLE,
              "The VSUM could not be built because of a problem with the build environment, not"
                  + " with the uploaded files. Try again later:\n"
                  + summary,
              HttpStatus.SERVICE_UNAVAILABLE,
              null);
      case INTERNAL ->
          new MethodologistSetupException(
              VsumErrorCodes.INTERNAL_ERROR,
              "The VSUM project generated by the setup service does not build. This is a problem"
                  + " of the setup service or its configuration (e.g. incompatible library"
                  + " versions), not of the uploaded files:\n"
                  + summary,
              HttpStatus.INTERNAL_SERVER_ERROR,
              null);
      case INPUT ->
          new MethodologistSetupException(
              VsumErrorCodes.BUILD_FAILED,
              "The VSUM build failed:\n" + summary,
              HttpStatus.UNPROCESSABLE_CONTENT,
              null);
    };
  }

  /** Describes the module that was being built when the output ends, or returns "". */
  private static String runningModule(List<String> output) {
    String module = null;
    for (String line : output) {
      Matcher matcher = MODULE_START.matcher(line);
      if (matcher.find()) {
        module = matcher.group(1);
      }
    }
    if (module == null) {
      return "";
    }
    String description = MODULE_DESCRIPTIONS.get(module);
    return description == null
        ? " (it was building " + module + ")"
        : " (it was building " + description + ", module " + module + ")";
  }

  /**
   * Extracts the relevant part of the Maven output: all {@code [ERROR]} lines (which include
   * compiler and test failures) or, if there are none, the tail of the log.
   */
  String summarize(Path logFile) {
    return summarize(readLog(logFile));
  }

  private String summarize(List<String> output) {
    int limit = Math.max(1, properties.getErrorOutputLines());
    List<String> errors = new ArrayList<>();
    Deque<String> tail = new ArrayDeque<>();
    boolean inError = false;
    for (String line : output) {
      if (line.startsWith("[")) {
        inError = line.startsWith("[ERROR]") && !isMavenBoilerplate(line);
      }
      // lines without a log level prefix continue the previous message, e.g. test failures
      if (inError && !line.isBlank() && errors.size() < limit) {
        errors.add(line);
      }
      tail.addLast(line);
      if (tail.size() > limit) {
        tail.removeFirst();
      }
    }
    if (output.isEmpty()) {
      return "(no build output)";
    }
    return String.join("\n", errors.isEmpty() ? tail : errors);
  }

  /** Reads the build output, tolerating bytes that are not valid UTF-8. */
  private static List<String> readLog(Path logFile) {
    try {
      return new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8).lines().toList();
    } catch (IOException e) {
      log.warn("Could not read the build output {}: {}", logFile, e.getMessage());
      return List.of();
    }
  }

  private static boolean isMavenBoilerplate(String line) {
    String message = line.substring("[ERROR]".length()).trim();
    return message.isEmpty()
        || message.startsWith("-> [Help")
        || message.startsWith("To see the full stack trace")
        || message.startsWith("Re-run Maven")
        || message.startsWith("For more information about the errors")
        || message.startsWith("[Help")
        || message.startsWith("After correcting the problems")
        || message.startsWith("mvn <args>")
        || message.startsWith("See ");
  }

  private static void destroy(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
  }

  private static MethodologistSetupException interrupted(InterruptedException e) {
    return new MethodologistSetupException(
        VsumErrorCodes.BUILD_ERROR,
        "The VSUM build was interrupted",
        HttpStatus.INTERNAL_SERVER_ERROR,
        e);
  }
}
