package tools.vitruv.methodologist.setup.vsum.service.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.VsumBuildProperties;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;

class MavenBuildRunnerTest {

  @TempDir Path directory;

  @Test
  void summaryContainsErrorsWithTheirContinuationLinesButNoBoilerplate() throws IOException {
    Path log = directory.resolve("build.log");
    Files.write(
        log,
        List.of(
            "[INFO] Building model",
            "[ERROR]   VSUMExampleTest.constraintsCanBeEvaluated:29 Constraints cannot be evaluated:",
            "- context model::Component inv broken: self.x",
            "    1:5 Unknown feature 'x'",
            "[INFO] Results:",
            "[ERROR] Failed to execute goal surefire on project vsum: There are test failures.",
            "[ERROR] See /tmp/vsum-build-1/project/vsum/target/surefire-reports for the results.",
            "[ERROR] -> [Help 1]",
            "[ERROR] "));

    String summary = new MavenBuildRunner(new VsumBuildProperties()).summarize(log);

    assertEquals(
        String.join(
            "\n",
            "[ERROR]   VSUMExampleTest.constraintsCanBeEvaluated:29 Constraints cannot be evaluated:",
            "- context model::Component inv broken: self.x",
            "    1:5 Unknown feature 'x'",
            "[ERROR] Failed to execute goal surefire on project vsum: There are test failures."),
        summary);
  }

  @Test
  void summaryFallsBackToTheTailOfTheLog() throws IOException {
    Path log = directory.resolve("build.log");
    Files.write(log, List.of("first", "second", "third"));
    VsumBuildProperties properties = new VsumBuildProperties();
    properties.setErrorOutputLines(2);

    assertEquals("second\nthird", new MavenBuildRunner(properties).summarize(log));
  }

  @Test
  void reportsAMissingMavenExecutable() {
    VsumBuildProperties properties = new VsumBuildProperties();
    properties.setMavenCommand("/does/not/exist/mvn");

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_ERROR, exception.getErrorCode());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void stopsBuildsThatExceedTheTimeout() throws IOException {
    Path fakeMaven = directory.resolve("mvn");
    Files.writeString(fakeMaven, "#!/bin/sh\nsleep 30\n");
    assertTrue(fakeMaven.toFile().setExecutable(true));
    VsumBuildProperties properties = new VsumBuildProperties();
    properties.setMavenCommand(fakeMaven.toString());
    properties.setBuildTimeout(Duration.ofMillis(500));

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_TIMEOUT, exception.getErrorCode());
    assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void reportsFailedBuildsWithTheirErrors() throws IOException {
    Path fakeMaven = directory.resolve("mvn");
    Files.writeString(fakeMaven, "#!/bin/sh\necho '[ERROR] compilation failed'\nexit 1\n");
    assertTrue(fakeMaven.toFile().setExecutable(true));
    VsumBuildProperties properties = new VsumBuildProperties();
    properties.setMavenCommand(fakeMaven.toString());

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_FAILED, exception.getErrorCode());
    assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exception.getStatus());
    assertTrue(exception.getMessage().contains("[ERROR] compilation failed"));
  }
}
