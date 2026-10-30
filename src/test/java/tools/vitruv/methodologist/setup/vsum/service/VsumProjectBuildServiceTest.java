package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildRunner;
import tools.vitruv.methodologist.setup.vsum.service.constraints.ConstraintsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectGenerator;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.views.NeoJoinViewDefinitionProcessor;

class VsumProjectBuildServiceTest {

  private static final VsumBuildRequest TEMPLATE_REQUEST =
      new VsumBuildRequest(
          List.of(fixture("model.ecore"), fixture("model2.ecore")),
          List.of(fixture("templateReactions.reactions")),
          List.of(fixture("constraints.ocl")),
          List.of(text("components.nj", "view stub")));

  private final VsumBuildProperties properties = new VsumBuildProperties();
  private final VsumProjectBuildService service =
      new VsumProjectBuildService(
          new MetamodelProcessor(),
          new ReactionsProcessor(),
          new ConstraintsProcessor(),
          new NeoJoinViewDefinitionProcessor(),
          new ProjectGenerator(properties),
          new MavenBuildRunner(properties),
          properties);

  @TempDir Path projectRoot;

  @Test
  void generatesAProjectFollowingTheMethodologistTemplate() throws IOException {
    service.generateProject(TEMPLATE_REQUEST, projectRoot);

    for (String file :
        List.of(
            "pom.xml",
            "model/pom.xml",
            "model/.project",
            "model/plugin.xml",
            "model/META-INF/MANIFEST.MF",
            "model/workflow/generate.mwe2",
            "model/src/main/ecore/model.ecore",
            "model/src/main/ecore/model.genmodel",
            "model/src/main/ecore/model2.genmodel",
            "consistency/pom.xml",
            "consistency/src/main/reactions/tools/vitruv/methodologisttemplate/consistency/"
                + "templateReactions.reactions",
            "consistency/src/main/constraints/tools/vitruv/methodologisttemplate/consistency/"
                + "constraints.ocl",
            "views/src/main/neojoin/components.nj",
            "vsum/pom.xml",
            "vsum/src/main/java/tools/vitruv/methodologisttemplate/vsum/VSUMExample.java",
            "vsum/src/test/java/tools/vitruv/methodologisttemplate/vsum/VSUMExampleTest.java")) {
      assertTrue(Files.isRegularFile(projectRoot.resolve(file)), file);
    }

    String rootPom = Files.readString(projectRoot.resolve("pom.xml"));
    assertTrue(rootPom.contains("<version>${vitruv.change.version}</version>"), rootPom);
    assertTrue(rootPom.contains("<vitruv.change.version>4.0.1</vitruv.change.version>"), rootPom);
    assertTrue(rootPom.contains("<vitruv.dsls.version>4.0.0</vitruv.dsls.version>"), rootPom);

    String workflow = Files.readString(projectRoot.resolve("model/workflow/generate.mwe2"));
    assertTrue(
        workflow.contains(
            "platform:/resource/tools.vitruv.methodologisttemplate.model/src/main/ecore/"
                + "model2.genmodel"),
        workflow);

    String main =
        Files.readString(
            projectRoot.resolve(
                "vsum/src/main/java/tools/vitruv/methodologisttemplate/vsum/VSUMExample.java"));
    assertTrue(
        main.contains(
            "new mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification()"),
        main);
    assertTrue(
        main.contains("tools.vitruv.methodologisttemplate.model.model2.Model2Package.eINSTANCE"),
        main);
    assertTrue(
        main.contains("\"tools/vitruv/methodologisttemplate/consistency/constraints.ocl\""), main);

    assertTrue(
        Files.readString(projectRoot.resolve("vsum/pom.xml")).contains("vitruvocl-language"));
  }

  @ParameterizedTest
  @CsvSource({
    "VSUM_INTERNAL_ERROR, true",
    "VSUM_BUILD_UNAVAILABLE, true",
    "VSUM_BUILD_FAILED, false",
    "VSUM_BUILD_TIMEOUT, false"
  })
  void keepsTheWorkspacesOfFailuresNotCausedByTheUploads(String errorCode, boolean kept)
      throws IOException {
    MavenBuildRunner mavenBuildRunner = mock(MavenBuildRunner.class);
    doThrow(
            new MethodologistSetupException(
                errorCode, "failed", HttpStatus.INTERNAL_SERVER_ERROR, null))
        .when(mavenBuildRunner)
        .build(any(), any());
    VsumProjectBuildService failing =
        new VsumProjectBuildService(
            new MetamodelProcessor(),
            new ReactionsProcessor(),
            new ConstraintsProcessor(),
            new NeoJoinViewDefinitionProcessor(),
            new ProjectGenerator(properties),
            mavenBuildRunner,
            properties);
    Set<Path> before = buildWorkspaces();

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class, () -> failing.buildProjectJar(TEMPLATE_REQUEST));

    assertEquals(errorCode, exception.getErrorCode());
    Set<Path> added = new HashSet<>(buildWorkspaces());
    added.removeAll(before);
    assertEquals(kept ? 1 : 0, added.size(), added.toString());
    for (Path workspace : added) {
      assertTrue(Files.isDirectory(workspace.resolve("project/vsum")), "the generated project");
      try (Stream<Path> paths = Files.walk(workspace)) {
        for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
    }
  }

  @Test
  void leavesOutVitruvOclWithoutConstraints() throws IOException {
    VsumBuildRequest request =
        new VsumBuildRequest(List.of(fixture("model.ecore")), List.of(), List.of(), List.of());

    service.generateProject(request, projectRoot);

    assertFalse(
        Files.readString(projectRoot.resolve("vsum/pom.xml")).contains("vitruvocl-language"));
    String main =
        Files.readString(
            projectRoot.resolve(
                "vsum/src/main/java/tools/vitruv/methodologisttemplate/vsum/VSUMExample.java"));
    assertFalse(main.contains("import tools.vitruv.dsls.vitruvocl"), main);
  }

  /**
   * A typo in a class name is reported at its line in the upload, as one problem. Runs the real
   * Maven build.
   */
  @Test
  @EnabledIfSystemProperty(named = "vsum.integration", matches = "true")
  void reportsReactionsErrorsAtTheirLineInTheUpload() {
    String reactions =
        fixture("templateReactions.reactions")
            .text()
            .replace("val entity = new model2::Entity", "val entity = new model2::Entitiy");
    VsumBuildException exception =
        assertThrows(
            VsumBuildException.class,
            () ->
                service.buildProjectJar(
                    new VsumBuildRequest(
                        List.of(fixture("model.ecore"), fixture("model2.ecore")),
                        List.of(text("my.reactions", reactions)),
                        List.of(),
                        List.of())));

    assertEquals(VsumErrorCodes.BUILD_FAILED, exception.getErrorCode());
    assertFalse(exception.getMessage().contains("vsum-build-"), exception.getMessage());
    BuildProblem problem = exception.getProblems().get(0);
    assertEquals(Source.REACTIONS, problem.source());
    assertEquals("my.reactions", problem.file());
    assertEquals(
        lineContaining(reactions, "model2::Entitiy"), problem.line(), exception.getMessage());
    assertTrue(problem.message().contains("Entitiy"), problem.message());
    assertTrue(problem.excerpt().contains("new model2::Entitiy"), problem.excerpt());
  }

  /**
   * A syntax error is reported without the errors that only follow from it. Runs the real Maven
   * build.
   */
  @Test
  @EnabledIfSystemProperty(named = "vsum.integration", matches = "true")
  void reportsOnlyTheSyntaxErrorOfAFile() {
    String reactions =
        fixture("templateReactions.reactions")
            .text()
            .replace("reaction ComponentRenamed {", "reaction ComponentRenamed {{");
    int brokenLine = lineContaining(reactions, "reaction ComponentRenamed {{");

    VsumBuildException exception =
        assertThrows(
            VsumBuildException.class,
            () ->
                service.buildProjectJar(
                    new VsumBuildRequest(
                        List.of(fixture("model.ecore"), fixture("model2.ecore")),
                        List.of(text("my.reactions", reactions)),
                        List.of(),
                        List.of())));

    List<BuildProblem> problems = exception.getProblems();
    assertTrue(
        problems.stream().allMatch(problem -> "my.reactions".equals(problem.file())),
        exception.getMessage());
    assertTrue(
        problems.stream()
            .allMatch(problem -> problem.line() != null && problem.line() >= brokenLine),
        exception.getMessage());
    assertTrue(problems.size() <= 2, exception.getMessage());
  }

  /** A constraint that cannot be evaluated is reported at its line. Runs the real Maven build. */
  @Test
  @EnabledIfSystemProperty(named = "vsum.integration", matches = "true")
  void reportsConstraintsThatCannotBeEvaluatedAtTheirLine() {
    String constraints =
        fixture("constraints.ocl")
            .text()
            .replace("self.entities.size() >= 2", "self.entitiez.size() >= 2");
    VsumBuildException exception =
        assertThrows(
            VsumBuildException.class,
            () ->
                service.buildProjectJar(
                    new VsumBuildRequest(
                        List.of(fixture("model.ecore"), fixture("model2.ecore")),
                        List.of(fixture("templateReactions.reactions")),
                        List.of(text("rules.ocl", constraints)),
                        List.of())));

    assertEquals(VsumErrorCodes.BUILD_FAILED, exception.getErrorCode());
    BuildProblem problem = exception.getProblems().get(0);
    assertEquals(Source.CONSTRAINTS, problem.source(), exception.getMessage());
    assertEquals("rules.ocl", problem.file());
    assertEquals(
        lineContaining(constraints, "inv LinkHasAtLeastTwoEntities"),
        problem.line(),
        exception.getMessage());
    assertTrue(problem.message().contains("LinkHasAtLeastTwoEntities"), problem.message());
  }

  private static int lineContaining(String text, String fragment) {
    List<String> lines = text.lines().toList();
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains(fragment)) {
        return i + 1;
      }
    }
    throw new IllegalArgumentException(fragment);
  }

  /**
   * Runs the real Maven build. Needs Maven and access to the Vitruvius artifacts, so it only runs
   * with {@code -Dvsum.integration=true}.
   */
  @Test
  @EnabledIfSystemProperty(named = "vsum.integration", matches = "true")
  void buildsAnExecutableVsumJar() throws IOException {
    byte[] jar = service.buildProjectJar(TEMPLATE_REQUEST);

    Set<String> entries = new HashSet<>();
    try (JarInputStream input = new JarInputStream(new ByteArrayInputStream(jar))) {
      assertTrue(
          input
              .getManifest()
              .getMainAttributes()
              .getValue("Main-Class")
              .equals(ProjectLayout.VSUM_PACKAGE + "." + ProjectLayout.VSUM_MAIN_CLASS));
      for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
        entries.add(entry.getName());
      }
    }
    assertTrue(
        entries.contains(
            "mir/reactions/model2Model2/Model2Model2ChangePropagationSpecification.class"));
    assertTrue(
        entries.contains("tools/vitruv/methodologisttemplate/model/model/ModelPackage.class"));
    assertTrue(entries.contains("tools/vitruv/methodologisttemplate/consistency/constraints.ocl"));
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
