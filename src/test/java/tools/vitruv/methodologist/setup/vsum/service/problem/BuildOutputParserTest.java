package tools.vitruv.methodologist.setup.vsum.service.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildFailedException;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.ProjectSources.SourceFile;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

class BuildOutputParserTest {

  // the project root of the original report, so that the recorded paths match
  private static final Path ROOT = Path.of("/tmp/vsum-build-15567868719241164154/project");
  private static final Path REACTIONS = ProjectLayout.reactionsDirectory(ROOT);

  private static final String CAD_REACTIONS =
      """
      import "http://example.org/cad" as cad
      import "http://example.org/brake" as brake

      import java.io.File

      reactions: cadToBrake_Case_Model
      in reaction to changes in cad
      execute actions in brake
      """;

  @Test
  void reportsTheRootCauseOfTheOriginalReportAndHidesItsFollowUpErrors() {
    ProjectSources sources = new ProjectSources(ROOT);
    sources.add(
        Source.REACTIONS,
        REACTIONS.resolve("reaction-1793269485319.reactions"),
        upload("reaction-1793269485319.reactions", CAD_REACTIONS),
        null);
    sources.add(
        Source.REACTIONS,
        REACTIONS.resolve("reaction-1793269415840.reactions"),
        upload("reaction-1793269415840.reactions", "a\nb\nimport java.io.File\n"),
        null);
    String file1 = "file:" + REACTIONS.resolve("reaction-1793269485319.reactions");
    String file2 = "file:" + REACTIONS.resolve("reaction-1793269415840.reactions");

    List<BuildProblem> problems =
        BuildOutputParser.parse(
            failed(
                "[ERROR] ERROR:mismatched input 'java' expecting RULE_STRING ("
                    + file1
                    + " line : 4 column : 8)",
                "[ERROR] ERROR:cad cannot be resolved. (" + file1 + " line : 18 column : 31)",
                "[ERROR] ERROR:CAD_Model cannot be resolved. (" + file1 + " line : 18 column : 36)",
                "[ERROR] ERROR:File cannot be resolved. (" + file1 + " line : 26 column : 51)",
                "[ERROR] ERROR:The method createAndRegisterRoot(unknown) from the type"
                    + " CadToBrake_Case_ModelRoutinesFacade refers to the missing type unknown ("
                    + file1
                    + " line : 15 column : 10)",
                "[ERROR] ERROR:mismatched input 'java' expecting RULE_STRING ("
                    + file2
                    + " line : 3 column : 8)",
                "[ERROR] ERROR:File cannot be resolved. (" + file2 + " line : 23 column : 48)",
                "[ERROR] Failed to execute goal"
                    + " org.eclipse.xtext:xtext-maven-plugin:2.43.0:generate"
                    + " (default) on project tools.vitruv.methodologisttemplate.consistency:"
                    + " Execution failed due to a severe validation error. -> [Help 1]"),
            sources);

    assertEquals(2, problems.size(), problems.toString());
    BuildProblem first = problems.get(0);
    assertEquals(Source.REACTIONS, first.source());
    assertEquals("reaction-1793269485319.reactions", first.file());
    assertEquals(4, first.line());
    assertEquals(8, first.column());
    assertEquals("mismatched input 'java' expecting RULE_STRING", first.message());
    assertEquals("import java.io.File\n       ^", first.excerpt());
    assertEquals(4, first.hiddenFollowUpErrors());
    assertEquals(1, problems.get(1).hiddenFollowUpErrors());

    String message = ProblemReport.render("The VSUM build failed:", problems);
    assertFalse(message.contains("/tmp/vsum-build-"), message);
    assertTrue(message.contains("(4 follow-up errors hidden)"), message);
  }

  @Test
  void reportsTheLinesOfTheUploadWhenJavaImportsWereMoved() {
    ProjectSources sources = new ProjectSources(ROOT);
    // the written file has the Java import first; the upload had it on line 3
    sources.add(
        Source.REACTIONS,
        REACTIONS.resolve("moved.reactions"),
        upload("moved.reactions", "import \"a\" as a\nimport \"b\" as b\nimport java.io.File\n"),
        new int[] {3, 1, 2, 4});

    List<BuildProblem> problems =
        BuildOutputParser.parse(
            failed(
                "[ERROR] ERROR:a cannot be resolved. (file:"
                    + REACTIONS.resolve("moved.reactions")
                    + " line : 2 column : 16)"),
            sources);

    assertEquals(1, problems.get(0).line(), "line 2 of the written file is line 1 of the upload");
  }

  @Test
  void reportsAnUnknownNameOnceWithItsOtherLines() {
    ProjectSources sources = new ProjectSources(ROOT);
    String text = "l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\n";
    sources.add(
        Source.REACTIONS, REACTIONS.resolve("x.reactions"), upload("x.reactions", text), null);
    String file = "file:" + REACTIONS.resolve("x.reactions");

    List<BuildProblem> problems =
        BuildOutputParser.parse(
            failed(
                "[ERROR] ERROR:Entitiy cannot be resolved. (" + file + " line : 3 column : 5)",
                "[ERROR] ERROR:Entitiy cannot be resolved. (" + file + " line : 5 column : 5)",
                "[ERROR] ERROR:Entitiy cannot be resolved. (" + file + " line : 9 column : 5)",
                "[ERROR] ERROR:The method x(unknown) refers to the missing type unknown ("
                    + file
                    + " line : 7 column : 1)"),
            sources);

    assertEquals(1, problems.size(), problems.toString());
    assertEquals("'Entitiy' cannot be resolved (also in lines 5, 9)", problems.get(0).message());
    assertEquals(3, problems.get(0).line());
    assertEquals(1, problems.get(0).hiddenFollowUpErrors());
  }

  @Test
  void attributesCompileErrorsOfTheGeneratedModelCodeToTheMetamodel() {
    ProjectSources sources = new ProjectSources(ROOT);
    String ecore =
        "<ecore:EPackage name=\"model2\">\n"
            + "  <eClassifiers xsi:type=\"ecore:EClass\" name=\"Root\"/>\n"
            + "  <eClassifiers xsi:type=\"ecore:EClass\" name=\"Entity\"/>\n"
            + "</ecore:EPackage>\n";
    SourceFile metamodel =
        sources.add(
            Source.METAMODEL,
            ProjectLayout.ecoreDirectory(ROOT).resolve("model2.ecore"),
            upload("upload.ecore", ecore),
            null);
    sources.addMetamodelPackage("tools.vitruv.methodologisttemplate.model.model2", metamodel);
    Path generated =
        ROOT.resolve(
            "model/target/generated-sources/ecore/tools/vitruv/methodologisttemplate/model/model2"
                + "/impl/EntityImpl.java");

    List<BuildProblem> problems =
        BuildOutputParser.parse(
            failed(
                "[ERROR] "
                    + generated
                    + ":[42,17] getClass() in EntityImpl cannot override"
                    + " getClass() in java.lang.Object",
                "  overridden method is final"),
            sources);

    BuildProblem problem = problems.get(0);
    assertEquals(Source.METAMODEL, problem.source());
    assertEquals("upload.ecore", problem.file());
    assertEquals(3, problem.line());
    assertTrue(
        problem.message().startsWith("the Java code generated for 'Entity'"), problem.message());
    assertTrue(problem.message().contains("overridden method is final"), problem.message());
    assertFalse(problem.message().contains("/tmp/"), problem.message());
    assertTrue(problem.hint().contains("getClass()"), problem.hint());
  }

  @Test
  void attributesCompileErrorsOfTheGeneratedReactionsCodeToTheSegmentsFile() {
    ProjectSources sources = new ProjectSources(ROOT);
    SourceFile file =
        sources.add(
            Source.REACTIONS, REACTIONS.resolve("a.reactions"), upload("a.reactions", "x"), null);
    sources.addSegment("Model2Model2", file);
    Path generated =
        ROOT.resolve(
            "consistency/target/generated-sources/reactions/mir/routines/model2Model2"
                + "/CreateRootRoutine.java");

    List<BuildProblem> problems =
        BuildOutputParser.parse(
            failed("[ERROR] " + generated + ":[30,9] incompatible types: int cannot be converted"),
            sources);

    assertEquals(Source.REACTIONS, problems.get(0).source());
    assertEquals("a.reactions", problems.get(0).file());
  }

  @Test
  void attributesCompileErrorsOfTheVsumModuleToTheService() {
    Path generated =
        ROOT.resolve("vsum/src/main/java/tools/vitruv/methodologisttemplate/vsum/VSUMExample.java");

    List<BuildProblem> problems =
        BuildOutputParser.parse(
            failed("[ERROR] " + generated + ":[12,8] cannot find symbol"),
            new ProjectSources(ROOT));

    assertEquals(Source.INTERNAL, problems.get(0).source());
    assertNull(problems.get(0).file());
  }

  @Test
  void fallsBackToTheModuleThatFailed() {
    List<BuildProblem> problems =
        BuildOutputParser.parse(
            failed(
                "[ERROR] Failed to execute goal org.codehaus.mojo:exec-maven-plugin:3.5.0:java"
                    + " (generate) on project tools.vitruv.methodologisttemplate.model: An"
                    + " exception occurred while executing the Java class."),
            new ProjectSources(ROOT));

    assertEquals(1, problems.size());
    assertEquals(Source.METAMODEL, problems.get(0).source());
    assertTrue(problems.get(0).message().contains("exec-maven-plugin"));
  }

  @Test
  void reportsInfrastructureFailuresAsOneProblem() {
    MavenBuildFailedException failure =
        new MavenBuildFailedException(
            VsumErrorCodes.BUILD_UNAVAILABLE,
            "The build environment failed:",
            List.of("[ERROR] Could not transfer artifact a:b:jar:1: Connection reset"),
            "[ERROR] Could not transfer artifact a:b:jar:1: Connection reset",
            HttpStatus.SERVICE_UNAVAILABLE);

    List<BuildProblem> problems = BuildOutputParser.parse(failure, new ProjectSources(ROOT));

    assertEquals(
        List.of(Source.INFRASTRUCTURE), problems.stream().map(BuildProblem::source).toList());
  }

  @Test
  void attributesFailedConstraintsOfTheSmokeTestToTheirLines(@TempDir Path root)
      throws IOException {
    ProjectSources sources = new ProjectSources(root);
    String ocl =
        "-- constraints\n"
            + "context model::Component\n"
            + "  inv nameSet: self.name <> null\n"
            + "context model::Component\n"
            + "  inv broken: self.x\n";
    SourceFile file =
        sources.add(
            Source.CONSTRAINTS,
            ProjectLayout.constraintsDirectory(root).resolve("rules.ocl"),
            upload("rules.ocl", ocl),
            null);
    sources.addConstraintResource("tools/vitruv/methodologisttemplate/consistency/rules.ocl", file);
    writeReport(
        root,
        "constraintsCanBeEvaluated",
        "org.opentest4j.AssertionFailedError",
        "Constraints in tools/vitruv/methodologisttemplate/consistency/rules.ocl cannot be"
            + " evaluated:\n- context model::Component inv broken: self.x\n    1:5 Unknown feature"
            + " 'x'\n ==> expected: &lt;true&gt; but was: &lt;false&gt;",
        "org.opentest4j.AssertionFailedError: ...");

    List<BuildProblem> problems =
        BuildOutputParser.parse(failed("[ERROR] Tests run: 2, Failures: 1"), sources);

    BuildProblem problem = problems.get(0);
    assertEquals(Source.CONSTRAINTS, problem.source());
    assertEquals("rules.ocl", problem.file());
    assertEquals(5, problem.line());
    assertEquals(
        "the constraint 'broken' cannot be evaluated: 1:5 Unknown feature 'x'", problem.message());
  }

  @Test
  void attributesExceptionsWhileCreatingTheVsumToTheRoutine(@TempDir Path root) throws IOException {
    ProjectSources sources = new ProjectSources(root);
    String reactions =
        "reactions: model2Model2\n"
            + "in reaction to changes in model\n"
            + "execute actions in model2\n"
            + "\n"
            + "routine createAndRegisterRoot(model::System system) {\n"
            + "}\n";
    SourceFile file =
        sources.add(
            Source.REACTIONS,
            ProjectLayout.reactionsDirectory(root).resolve("t.reactions"),
            upload("t.reactions", reactions),
            null);
    sources.addSegment("model2Model2", file);
    writeReport(
        root,
        "virtualModelCanBeCreatedAndReloaded",
        "java.lang.IllegalStateException",
        "no correspondence for Root",
        "java.lang.IllegalStateException: no correspondence for Root\n"
            + "\tat mir.routines.model2Model2.CreateAndRegisterRootRoutine$Update.run"
            + "(CreateAndRegisterRootRoutine.java:40)\n");

    List<BuildProblem> problems =
        BuildOutputParser.parse(failed("[ERROR] Tests run: 1, Errors: 1"), sources);

    BuildProblem problem = problems.get(0);
    assertEquals(Source.REACTIONS, problem.source());
    assertEquals("t.reactions", problem.file());
    assertEquals(5, problem.line());
    assertEquals(
        "creating the VSUM failed in routine 'createAndRegisterRoot': IllegalStateException: no"
            + " correspondence for Root",
        problem.message());
  }

  private static MavenBuildFailedException failed(String... output) {
    return new MavenBuildFailedException(
        VsumErrorCodes.BUILD_FAILED,
        "The VSUM build failed:",
        List.of(output),
        String.join("\n", output),
        HttpStatus.UNPROCESSABLE_CONTENT);
  }

  private static UploadedFile upload(String name, String text) {
    return new UploadedFile(name, text.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeReport(
      Path root, String testCase, String type, String message, String stackTrace)
      throws IOException {
    Path reports = ProjectLayout.vsumModule(root).resolve("target/surefire-reports");
    Files.createDirectories(reports);
    Files.writeString(
        reports.resolve("TEST-tools.vitruv.methodologisttemplate.vsum.VSUMExampleTest.xml"),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"VSUMExampleTest\">\n"
            + "<testcase name=\""
            + testCase
            + "\"><error type=\""
            + type
            + "\" message=\""
            + message.replace("\n", "&#10;")
            + "\"><![CDATA["
            + stackTrace
            + "]]></error></testcase>\n</testsuite>\n");
  }
}
