package tools.vitruv.methodologist.setup.vsum.service.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.util.unit.DataSize;
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
            "[ERROR]   VSUMExampleTest.constraintsCanBeEvaluated:29 Constraints cannot be"
                + " evaluated:",
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
            "[ERROR]   VSUMExampleTest.constraintsCanBeEvaluated:29 Constraints cannot be"
                + " evaluated:",
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

  @Test
  void buildsOfflineWithTheConfiguredRepositoryAndLocksIt() {
    VsumBuildProperties properties = new VsumBuildProperties();
    properties.setOffline(true);
    properties.setMavenRepository("/opt/vsum-m2");
    properties.setMavenArguments(List.of("-e"));

    List<String> command = new MavenBuildRunner(properties).command(true);

    assertEquals(
        List.of(
            "mvn",
            "--batch-mode",
            "--no-transfer-progress",
            "--offline",
            "-Dmaven.repo.local=/opt/vsum-m2",
            "-Daether.syncContext.named.factory=file-lock",
            "-Daether.syncContext.named.nameMapper=file-gav",
            "clean",
            "verify",
            "-e"),
        command);
  }

  @Test
  void buildsOnlineWithMavensDefaultRepositoryByDefault() {
    List<String> command = new MavenBuildRunner(new VsumBuildProperties()).command(false);

    assertFalse(command.contains("--offline"), command.toString());
    assertTrue(command.stream().noneMatch(arg -> arg.startsWith("-Dmaven.repo.local")));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void retriesInfrastructureFailuresAndReportsThemAsUnavailable() throws IOException {
    VsumBuildProperties properties =
        fakeMaven(
            "echo '[ERROR] Failed to execute goal on project x: Could not transfer artifact"
                + " a:b:jar:1 from/to central: Connection reset'\nexit 1");

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_UNAVAILABLE, exception.getErrorCode());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    assertTrue(exception.getMessage().contains("not with the uploaded files"));
    assertTrue(exception.getMessage().contains("Connection reset"));
    assertEquals(2, calls().size(), "one retry");
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void succeedsWhenTheRetryOfAnInfrastructureFailureSucceeds() throws IOException {
    VsumBuildProperties properties =
        fakeMaven(
            "if [ $(wc -l < calls) -eq 1 ]; then"
                + " echo '[ERROR] Could not resolve dependencies for project x'; exit 1; fi");

    new MavenBuildRunner(properties).build(directory, directory);

    assertEquals(2, calls().size());
    assertTrue(Files.exists(directory.resolve("build-retry-1.log")));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void retriesOnlineWhenADependencyIsMissingOffline() throws IOException {
    VsumBuildProperties properties =
        fakeMaven(
            "case \"$*\" in *--offline*) echo '[ERROR] Cannot access central in offline mode and"
                + " the artifact a:b:jar:1 has not been downloaded from it before.'; exit 1;;"
                + " esac");
    properties.setOffline(true);

    new MavenBuildRunner(properties).build(directory, directory);

    List<String> calls = calls();
    assertEquals(2, calls.size());
    assertTrue(calls.get(0).contains("--offline"), calls.get(0));
    assertFalse(calls.get(1).contains("--offline"), calls.get(1));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void reportsMissingOfflineDependenciesWithoutOnlineFallback() throws IOException {
    VsumBuildProperties properties =
        fakeMaven(
            "echo '[ERROR] Cannot access central in offline mode and the artifact a:b:jar:1 has"
                + " not been downloaded from it before.'\nexit 1");
    properties.setOffline(true);
    properties.setOnlineFallback(false);

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_UNAVAILABLE, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("must be redeployed"), exception.getMessage());
    assertEquals(1, calls().size(), "retrying offline cannot help");
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void reportsBrokenGeneratedCodeAsInternalErrorWithoutRetrying() throws IOException {
    VsumBuildProperties properties =
        fakeMaven(
            "echo '[ERROR] Failed to execute goal compile on project"
                + " tools.vitruv.methodologisttemplate.vsum: Compilation failure'\nexit 1");

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.INTERNAL_ERROR, exception.getErrorCode());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    assertTrue(exception.getMessage().contains("not of the uploaded files"));
    assertEquals(1, calls().size());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void doesNotRetryFailuresCausedByTheUploads() throws IOException {
    VsumBuildProperties properties =
        fakeMaven("echo '[ERROR] ERROR:cad cannot be resolved. (file:/a.reactions)'\nexit 1");

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_FAILED, exception.getErrorCode());
    assertEquals(1, calls().size());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void timeoutsNameTheModuleThatWasBuilding() throws IOException {
    VsumBuildProperties properties =
        fakeMaven(
            "echo '[INFO] Building tools.vitruv.methodologisttemplate.model 0.1.0-SNAPSHOT [2/4]'\n"
                + "echo '[INFO] Building tools.vitruv.methodologisttemplate.consistency"
                + " 0.1.0-SNAPSHOT [3/4]'\nsleep 30");
    properties.setBuildTimeout(Duration.ofMillis(1500));

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_TIMEOUT, exception.getErrorCode());
    assertTrue(
        exception
            .getMessage()
            .contains(
                "it was building the reactions, module tools.vitruv.methodologisttemplate"
                    + ".consistency"),
        exception.getMessage());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void refusesToBuildWithoutEnoughFreeDiskSpace() throws IOException {
    VsumBuildProperties properties = fakeMaven("exit 0");
    properties.setMinFreeDiskSpace(DataSize.ofTerabytes(1_000_000));

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> new MavenBuildRunner(properties).build(directory, directory));

    assertEquals(VsumErrorCodes.BUILD_UNAVAILABLE, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("low on disk space"), exception.getMessage());
    assertTrue(calls().isEmpty(), "Maven must not be started");
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void passesTheConfiguredMavenOpts() throws IOException {
    VsumBuildProperties properties = fakeMaven("echo \"$MAVEN_OPTS\" > maven-opts");
    properties.setMavenOpts("-Xmx123m");

    new MavenBuildRunner(properties).build(directory, directory);

    assertEquals("-Xmx123m", Files.readString(directory.resolve("maven-opts")).trim());
  }

  /**
   * Creates a fake {@code mvn} that appends its arguments to {@code calls} in the project directory
   * and then runs the given shell script.
   */
  private VsumBuildProperties fakeMaven(String script) throws IOException {
    Path fakeMaven = directory.resolve("mvn");
    Files.writeString(fakeMaven, "#!/bin/sh\necho \"$*\" >> calls\n" + script + "\n");
    assertTrue(fakeMaven.toFile().setExecutable(true));
    VsumBuildProperties properties = new VsumBuildProperties();
    properties.setMavenCommand(fakeMaven.toString());
    properties.setMinFreeDiskSpace(DataSize.ofBytes(0));
    return properties;
  }

  private List<String> calls() throws IOException {
    Path calls = directory.resolve("calls");
    return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
  }
}
