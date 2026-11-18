package tools.vitruv.methodologist.setup.vsum.service.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

class NeoJoinViewDefinitionProcessorTest {

  private static final String MODEL_URI = "http://vitruv.tools/methodologisttemplate/model";
  private static final String MODEL2_URI = "http://vitruv.tools/methodologisttemplate/model2";
  private static final String SUBPACKAGE_URI = MODEL_URI + "/sub";

  private static final List<GeneratedMetamodel> METAMODELS =
      List.of(
          new GeneratedMetamodel(
              "model",
              MODEL_URI,
              "model.ecore",
              "model.genmodel",
              List.of(
                  new GeneratedMetamodel.GeneratedPackage(MODEL_URI, "x.model.ModelPackage"),
                  new GeneratedMetamodel.GeneratedPackage(SUBPACKAGE_URI, "x.model.SubPackage"))),
          new GeneratedMetamodel(
              "model2",
              MODEL2_URI,
              "model2.ecore",
              "model2.genmodel",
              List.of(
                  new GeneratedMetamodel.GeneratedPackage(MODEL2_URI, "x.model2.Model2Package"))));

  private final NeoJoinViewDefinitionProcessor processor = new NeoJoinViewDefinitionProcessor();

  @TempDir Path projectRoot;

  @Test
  void derivesTheNamesOfTheGeneratedClassesAndWritesTheViews() throws IOException {
    List<GeneratedViewType> viewTypes =
        processor.process(
            List.of(view("components.nj", "components"), view("my-entities.nj", "entities")),
            METAMODELS,
            projectRoot);

    assertEquals(2, viewTypes.size());
    GeneratedViewType components = viewTypes.get(0);
    assertEquals("Components", components.name());
    assertEquals("neojoin.viewtypes.components.ComponentsViewType", components.viewTypeClass());

    // the class is named after the file, its package after the export
    GeneratedViewType entities = viewTypes.get(1);
    assertEquals("my-entities.nj", entities.fileName());
    assertEquals("MyEntities", entities.name());
    assertEquals("entities", entities.exportName());
    assertEquals("http://example.org/views/entities", entities.nsUri());
    assertEquals("neojoin.viewtypes.entities.MyEntitiesViewType", entities.viewTypeClass());
    assertEquals("neojoin.viewtypes.entities.EntitiesPackage", entities.packageInterface());
    assertEquals(
        "target/generated-sources/neojoin/ecore/entities/my-entities.genmodel",
        entities.genmodelPath());

    Path views = ProjectLayout.viewsDirectory(projectRoot);
    assertTrue(Files.isRegularFile(views.resolve("components.nj")));
    assertTrue(Files.isRegularFile(views.resolve("my-entities.nj")));
  }

  @Test
  void writesNothingWithoutViews() throws IOException {
    assertEquals(List.of(), processor.process(List.of(), METAMODELS, projectRoot));
    assertFalse(Files.exists(ProjectLayout.viewtypeModule(projectRoot)));
  }

  @Test
  void namesViewTypesLikeNeoJoin() {
    assertEquals("Components", GeneratedViewType.pascalCase("components"));
    assertEquals("MyView2", GeneratedViewType.pascalCase("my_View-2"));
    // NeoJoin lower-cases everything but the first letter of each word
    assertEquals("Myview", GeneratedViewType.pascalCase("myView"));
    assertEquals("View", GeneratedViewType.pascalCase("_view"));
    assertEquals("a.b", GeneratedViewType.baseName("a.b.nj"));
    assertEquals("views", GeneratedViewType.baseName("views.NJ"));
  }

  @Test
  void rejectsFileNamesThatCannotNameAViewType() {
    BuildProblem problem =
        firstProblem(
            rejected(view("1st.nj", "first"), view("a.b.nj", "ab"), view("ok.nj", "ok")), 2);

    assertEquals(Source.VIEWS, problem.source());
    assertEquals("1st.nj", problem.file());
    assertEquals("the file name cannot be the name of a view type", problem.message());
    assertNull(problem.line());
    assertTrue(problem.hint().contains("start the file name with a letter"), problem.hint());
  }

  @Test
  void rejectsViewsWithoutExport() {
    BuildProblem problem =
        single(rejected(text("plain.nj", "import \"" + MODEL_URI + "\"\nfrom Component c\n")));

    assertEquals("plain.nj", problem.file());
    assertEquals("declares no export", problem.message());
    assertTrue(problem.hint().contains("export <name> to"), problem.hint());
  }

  @Test
  void ignoresExportsInComments() {
    BuildProblem problem =
        single(
            rejected(
                text(
                    "commented.nj",
                    "// export components to \"http://example.org/views/components\"\n"
                        + "/* export components to \"http://example.org/views/components\" */\n"
                        + "from Component c\n")));

    assertEquals("declares no export", problem.message());
  }

  @Test
  void rejectsViewTypeNamesUsedTwice() {
    // both files become the view type MyView
    BuildProblem problem =
        single(rejected(view("my-view.nj", "first"), view("My_View.nj", "second")));

    assertEquals("My_View.nj", problem.file());
    assertEquals("the view type 'MyView' is already defined by my-view.nj", problem.message());
    assertEquals(1, problem.line());
    assertEquals(1, problem.column());
  }

  @Test
  void rejectsViewTypeNamesOfAMetamodel() {
    List<GeneratedMetamodel> metamodels =
        List.of(
            new GeneratedMetamodel(
                "Components",
                MODEL_URI,
                "components.ecore",
                "components.genmodel",
                List.of(
                    new GeneratedMetamodel.GeneratedPackage(MODEL_URI, "x.ComponentsPackage"))));

    VsumBuildException exception =
        assertThrows(
            VsumBuildException.class,
            () -> processor.check(List.of(view("components.nj", "components")), metamodels));

    BuildProblem problem = single(exception);
    assertEquals(
        "the view type 'Components' has the same name as the view type of the metamodel"
            + " 'Components'",
        problem.message());
    assertTrue(problem.hint().contains("Rename the file"), problem.hint());
  }

  @Test
  void rejectsExportsToTheNsUriOfAMetamodelOrItsSubpackage() {
    VsumBuildException exception =
        rejected(
            text("a.nj", "export a to \"" + MODEL2_URI + "\"\n"),
            text("b.nj", "\n\nexport b to \"" + SUBPACKAGE_URI + "\"\n"));

    assertEquals(2, exception.getProblems().size(), exception.getProblems().toString());
    BuildProblem metamodel = exception.getProblems().get(0);
    assertEquals("a.nj", metamodel.file());
    assertEquals(
        "exports to '" + MODEL2_URI + "', which is the nsURI of an uploaded metamodel",
        metamodel.message());
    // points at the opening quote of the nsURI
    assertEquals(1, metamodel.line());
    assertEquals(13, metamodel.column());
    BuildProblem subpackage = exception.getProblems().get(1);
    assertEquals("b.nj", subpackage.file());
    assertEquals(3, subpackage.line());
  }

  @Test
  void reportsTheProblemsOfAllViewsAndWritesNothing() {
    VsumBuildException exception =
        assertThrows(
            VsumBuildException.class,
            () ->
                processor.process(
                    List.of(
                        text("none.nj", "from Component c"),
                        view("x.y.nj", "xy"),
                        view("valid.nj", "valid")),
                    METAMODELS,
                    projectRoot));

    assertEquals(VsumErrorCodes.VIEWS_ERROR, exception.getErrorCode());
    assertEquals(
        List.of("none.nj", "x.y.nj"),
        exception.getProblems().stream().map(BuildProblem::file).toList());
    assertFalse(Files.exists(ProjectLayout.viewsDirectory(projectRoot)));
  }

  /** A view definition exporting to its own nsURI. */
  private static UploadedFile view(String fileName, String exportName) {
    return text(
        fileName,
        "export "
            + exportName
            + " to \"http://example.org/views/"
            + exportName
            + "\"\n\nimport \""
            + MODEL_URI
            + "\"\n\nfrom Component c\ncreate Thing {\n    c.name\n}\n");
  }

  private VsumBuildException rejected(UploadedFile... views) {
    return assertThrows(
        VsumBuildException.class, () -> processor.check(List.of(views), METAMODELS));
  }

  /** The only problem. */
  private static BuildProblem single(VsumBuildException exception) {
    return firstProblem(exception, 1);
  }

  /** The first problem, after checking how many there are. */
  private static BuildProblem firstProblem(VsumBuildException exception, int expectedProblems) {
    assertEquals(
        expectedProblems, exception.getProblems().size(), exception.getProblems().toString());
    return exception.getProblems().get(0);
  }
}
