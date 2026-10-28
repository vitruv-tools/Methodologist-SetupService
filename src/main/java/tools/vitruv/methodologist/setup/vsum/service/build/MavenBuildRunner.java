package tools.vitruv.methodologist.setup.vsum.service.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.VsumBuildProperties;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;

/**
 * Runs {@code mvn clean verify} on a generated project. The build includes the generated smoke
 * test, so a successful build means the VSUM can actually be instantiated.
 */
@Slf4j
@Service
public class MavenBuildRunner {

  private static final String BUILD_LOG = "build.log";

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
   * Builds the project, waiting for a free build slot first.
   *
   * @param projectRoot root of the generated project
   * @param logDirectory directory receiving the build log; must be outside the project
   * @throws MethodologistSetupException when the build fails, times out, or cannot be started
   */
  public void build(Path projectRoot, Path logDirectory) {
    try {
      buildSlots.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw interrupted(e);
    }
    try {
      run(projectRoot, logDirectory.resolve(BUILD_LOG));
    } finally {
      buildSlots.release();
    }
  }

  private void run(Path projectRoot, Path logFile) {
    List<String> command = new ArrayList<>();
    command.add(properties.getMavenCommand());
    command.addAll(List.of("--batch-mode", "--no-transfer-progress", "clean", "verify"));
    command.addAll(properties.getMavenArguments());

    log.info("Building generated VSUM project in {}: {}", projectRoot, String.join(" ", command));
    Process process;
    try {
      process =
          new ProcessBuilder(command)
              .directory(projectRoot.toFile())
              .redirectErrorStream(true)
              .redirectOutput(logFile.toFile())
              .start();
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
            "The VSUM build did not finish within " + properties.getBuildTimeout(),
            HttpStatus.GATEWAY_TIMEOUT,
            null);
      }
    } catch (InterruptedException e) {
      destroy(process);
      Thread.currentThread().interrupt();
      throw interrupted(e);
    }

    if (process.exitValue() != 0) {
      log.warn("VSUM build failed with exit code {}; log: {}", process.exitValue(), logFile);
      throw new MethodologistSetupException(
          VsumErrorCodes.BUILD_FAILED,
          "The VSUM build failed:\n" + summarize(logFile),
          HttpStatus.UNPROCESSABLE_CONTENT,
          null);
    }
    log.info("VSUM build succeeded in {}", projectRoot);
  }

  /**
   * Extracts the relevant part of the Maven output: all {@code [ERROR]} lines (which include
   * compiler and test failures) or, if there are none, the tail of the log.
   */
  String summarize(Path logFile) {
    int limit = Math.max(1, properties.getErrorOutputLines());
    List<String> errors = new ArrayList<>();
    Deque<String> tail = new ArrayDeque<>();
    boolean[] inError = {false};
    try (Stream<String> lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
      lines.forEach(
          line -> {
            if (line.startsWith("[")) {
              inError[0] = line.startsWith("[ERROR]") && !isMavenBoilerplate(line);
            }
            // lines without a log level prefix continue the previous message, e.g. test failures
            if (inError[0] && !line.isBlank() && errors.size() < limit) {
              errors.add(line);
            }
            tail.addLast(line);
            if (tail.size() > limit) {
              tail.removeFirst();
            }
          });
    } catch (IOException | java.io.UncheckedIOException e) {
      return "(build output unavailable: " + e.getMessage() + ")";
    }
    return String.join("\n", errors.isEmpty() ? tail : errors);
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
