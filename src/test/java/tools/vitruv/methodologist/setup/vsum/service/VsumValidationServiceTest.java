package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildRunner;
import tools.vitruv.methodologist.setup.vsum.service.constraints.ConstraintsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectGenerator;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.views.NeoJoinChecker;
import tools.vitruv.methodologist.setup.vsum.service.views.NeoJoinViewDefinitionProcessor;

class VsumValidationServiceTest {

  private static final VsumBuildRequest TEMPLATE_REQUEST =
      new VsumBuildRequest(
          List.of(fixture("model.ecore"), fixture("model2.ecore")),
          List.of(fixture("templateReactions.reactions")),
          List.of(fixture("constraints.ocl")),
          List.of(fixture("components.nj")));

  private final VsumBuildProperties properties = new VsumBuildProperties();
  private final VsumValidationService service =
      new VsumValidationService(
          new MetamodelProcessor(), new ReactionsProcessor(), new NeoJoinChecker());

  @TempDir Path temporaryDirectory;

  @Test
  void validProjectsHaveNoProblems() {
    VsumValidationResult result = service.validate(TEMPLATE_REQUEST);

    assertTrue(result.valid());
    assertEquals(List.of(), result.problems());
  }

  @Test
  void reportsTheMetamodelProblemsTheBuildReportsBeforeMaven() {
    String metamodel =
        fixture("model2.ecore").text().replaceFirst("nsURI=\"[^\"]*\"", "nsURI=\"\"");
    VsumBuildRequest request =
        new VsumBuildRequest(
            List.of(fixture("model.ecore"), text("model2.ecore", metamodel)),
            List.of(fixture("templateReactions.reactions")),
            List.of(),
            List.of());

    VsumValidationResult result = service.validate(request);

    assertFalse(result.valid());
    assertEquals(Source.METAMODEL, result.problems().get(0).source());
    assertEquals("model2.ecore", result.problems().get(0).file());
    assertEquals(problemsOfTheBuild(request), result);
  }

  @Test
  void reportsTheReactionsProblemsTheBuildReportsBeforeMaven() {
    String reactions =
        fixture("templateReactions.reactions")
            .text()
            .replace("val entity = new model2::Entity", "val entity = new model2::Entitiy");
    VsumBuildRequest request =
        new VsumBuildRequest(
            List.of(fixture("model.ecore"), fixture("model2.ecore")),
            List.of(text("my.reactions", reactions)),
            List.of(),
            List.of());

    VsumValidationResult result = service.validate(request);

    assertFalse(result.valid());
    assertEquals("my.reactions", result.problems().get(0).file());
    assertEquals("Did you mean 'Entity'?", result.problems().get(0).hint());
    assertEquals(problemsOfTheBuild(request), result);
  }

  @Test
  void reportsTheViewProblemsTheBuildReportsBeforeMaven() {
    String view =
        fixture("components.nj").text().replace("supportedProtocols.size", "supportedProtocolz");
    VsumBuildRequest request =
        new VsumBuildRequest(
            List.of(fixture("model.ecore"), fixture("model2.ecore")),
            List.of(fixture("templateReactions.reactions")),
            List.of(),
            List.of(text("components.nj", view)));

    VsumValidationResult result = service.validate(request);

    assertFalse(result.valid());
    assertEquals(Source.VIEWS, result.problems().get(0).source());
    assertEquals("components.nj", result.problems().get(0).file());
    assertTrue(result.problems().get(0).message().contains("supportedProtocolz"));
    assertEquals(problemsOfTheBuild(request), result);
  }

  @Test
  void leavesNoFilesBehind() throws IOException {
    Set<Path> before = buildWorkspaces();

    service.validate(TEMPLATE_REQUEST);

    assertEquals(before, buildWorkspaces());
    assertFalse(Files.exists(Path.of("unwritten-project")));
  }

  /**
   * The only build slot is taken by a build whose Maven never finishes; the validation is answered
   * anyway.
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  void isAnsweredWhileABuildIsRunning() throws Exception {
    Path started = temporaryDirectory.resolve("started");
    Path maven = temporaryDirectory.resolve("mvn");
    Files.writeString(maven, "#!/bin/sh\necho started >> '" + started + "'\nexec sleep 120\n");
    Files.setPosixFilePermissions(maven, PosixFilePermissions.fromString("rwx------"));
    VsumBuildProperties buildProperties = new VsumBuildProperties();
    buildProperties.setMavenCommand(maven.toString());
    buildProperties.setMinFreeDiskSpace(DataSize.ofBytes(0));
    buildProperties.setMaxConcurrentBuilds(1);
    VsumProjectBuildService buildService =
        new VsumProjectBuildService(
            new MetamodelProcessor(),
            new ReactionsProcessor(),
            new ConstraintsProcessor(),
            new NeoJoinChecker(),
            new NeoJoinViewDefinitionProcessor(),
            new ProjectGenerator(buildProperties),
            new MavenBuildRunner(buildProperties),
            buildProperties);

    ExecutorService builds = Executors.newSingleThreadExecutor();
    try {
      Future<byte[]> running = builds.submit(() -> buildService.buildProjectJar(TEMPLATE_REQUEST));
      assertTimeoutPreemptively(
          Duration.ofSeconds(60),
          () -> {
            while (!Files.exists(started)) {
              Thread.sleep(50);
            }
          },
          "the build runs Maven");

      VsumValidationResult result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(30), () -> service.validate(TEMPLATE_REQUEST));

      assertTrue(result.valid());
      assertFalse(running.isDone(), "the build is still running");
    } finally {
      builds.shutdownNow();
      assertTrue(builds.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  /** Builds the project with a Maven build that must not be reached, returning its problems. */
  private VsumValidationResult problemsOfTheBuild(VsumBuildRequest request) {
    MavenBuildRunner mavenBuildRunner = mock(MavenBuildRunner.class);
    VsumProjectBuildService buildService =
        new VsumProjectBuildService(
            new MetamodelProcessor(),
            new ReactionsProcessor(),
            new ConstraintsProcessor(),
            new NeoJoinChecker(),
            new NeoJoinViewDefinitionProcessor(),
            new ProjectGenerator(properties),
            mavenBuildRunner,
            properties);

    VsumBuildException exception =
        assertThrows(VsumBuildException.class, () -> buildService.buildProjectJar(request));

    verify(mavenBuildRunner, never()).build(any(), any());
    return VsumValidationResult.of(exception.getProblems());
  }

  private static Set<Path> buildWorkspaces() throws IOException {
    try (Stream<Path> paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return new HashSet<>(
          paths
              .filter(
                  path ->
                      path.getFileName()
                          .toString()
                          .startsWith(VsumProjectBuildService.WORKSPACE_PREFIX))
              .toList());
    }
  }
}
