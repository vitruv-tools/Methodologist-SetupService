package tools.vitruv.methodologist.setup.vsum.service.reactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;

/** The checks of reactions against the loaded sample metamodels, before any Maven build. */
class ReactionsCheckerTest {

  private static final String HEADER =
      """
      import "http://vitruv.tools/methodologisttemplate/model" as model
      import "http://vitruv.tools/methodologisttemplate/model2" as model2

      reactions: modelToModel2
      in reaction to changes in model
      execute actions in model2
      """;

  private final ReactionsProcessor processor = new ReactionsProcessor();

  @TempDir Path projectRoot;

  private List<GeneratedMetamodel> metamodels;

  @BeforeEach
  void loadMetamodels() throws IOException {
    metamodels =
        new MetamodelProcessor()
            .process(List.of(fixture("model.ecore"), fixture("model2.ecore")), projectRoot);
  }

  @Test
  void acceptsTheTemplateReactions() throws IOException {
    processor.process(List.of(fixture("templateReactions.reactions")), metamodels, projectRoot);
  }

  @Test
  void suggestsTheClassForAMisspelledClass() {
    BuildProblem problem =
        single(
            HEADER
                + """
                routine create(model::Component component) {
                  create {
                    val entity = new model2::Entitiy
                  }
                }
                """);

    assertEquals(Source.REACTIONS, problem.source());
    assertEquals(9, problem.line());
    assertEquals(30, problem.column());
    assertEquals(
        "'Entitiy' is not a class of the metamodel 'model2'"
            + " (http://vitruv.tools/methodologisttemplate/model2)",
        problem.message());
    assertEquals("Did you mean 'Entity'?", problem.hint());
    assertEquals(
        "    val entity = new model2::Entitiy\n                             ^", problem.excerpt());
  }

  @Test
  void listsTheClassesIfNoneIsClose() {
    BuildProblem problem =
        single(HEADER + "reaction X {\n after element model::Banana created\n call {}\n}\n");

    assertEquals(
        "Available classes: Component, Link, Protocol, Device, Server, System, Router",
        problem.hint());
  }

  @Test
  void reportsAMisspelledAliasOnceWithTheOtherLines() {
    BuildProblem problem =
        single(
            HEADER
                + """
                routine a(modl::System system) {
                  match {
                    require absence of modl::Component corresponding to system
                  }
                }
                """);

    assertEquals(7, problem.line());
    assertEquals(
        "'modl' is not a metamodel imported in this file (also in lines 9)", problem.message());
    assertEquals("Did you mean 'model'?", problem.hint());
  }

  @Test
  void checksTheMetamodelsOfTheSegment() {
    BuildProblem problem =
        single(HEADER.replace("execute actions in model2", "execute actions in model3"));

    assertEquals(6, problem.line());
    assertEquals(20, problem.column());
    assertEquals("'model3' is not a metamodel imported in this file", problem.message());
    assertEquals("Did you mean 'model' or 'model2'?", problem.hint());
  }

  @Test
  void suggestsTheFeatureOfTheRightKind() {
    BuildProblem problem =
        single(
            HEADER
                + "reaction X {\n"
                + "  after element model::Component inserted in model::System[component]\n"
                + "  call {}\n}\n");

    assertEquals(8, problem.line());
    assertEquals("'System' has no reference 'component'", problem.message());
    assertEquals("Did you mean 'components'?", problem.hint());
  }

  @Test
  void tellsToUseElementChangesForReferences() {
    BuildProblem problem =
        single(
            HEADER
                + "reaction X {\n  after attribute replaced at model::System[components]\n"
                + "  call {}\n}\n");

    assertEquals("'System.components' is a reference, not an attribute", problem.message());
    assertEquals("Use 'after element inserted in model::System[components]'", problem.hint());
  }

  @Test
  void tellsToUseAttributeChangesForAttributes() {
    BuildProblem problem =
        single(
            HEADER
                + "reaction X {\n  after element replaced at model::Component[name]\n"
                + "  call {}\n}\n");

    assertEquals("'Component.name' is an attribute, not a reference", problem.message());
    assertEquals("Use 'after attribute replaced at model::Component[name]'", problem.hint());
  }

  @Test
  void checksTheMultiplicityOfTheChangedReference() {
    BuildProblem replaced =
        single(
            HEADER
                + "reaction X {\n  after element replaced at model::System[components]\n"
                + "  call {}\n}\n");
    BuildProblem inserted =
        single(
            HEADER
                + "reaction X {\n  after element inserted in model::Link[protocol]\n"
                + "  call {}\n}\n");

    assertEquals(
        "'System.components' is many-valued, so its elements are not replaced", replaced.message());
    assertEquals(
        "'Link.protocol' is single-valued, so elements cannot be inserted in it",
        inserted.message());
    assertEquals("Use 'replaced at' for single-valued references", inserted.hint());
  }

  @Test
  void checksThatTheElementFitsTheReference() {
    BuildProblem problem =
        single(
            HEADER
                + "reaction X {\n"
                + "  after element model::Protocol inserted in model::System[components]\n"
                + "  call {}\n}\n");

    assertEquals(8, problem.line());
    assertEquals(24, problem.column());
    assertEquals(
        "elements of type 'Protocol' cannot be contained in 'System.components', which holds"
            + " 'Component'",
        problem.message());
  }

  @Test
  void rejectsCreatingAbstractClassesAndNamesTheConcreteOnes() {
    BuildProblem problem =
        single(
            "import \"http://www.eclipse.org/emf/2002/Ecore\" as ecore\n"
                + HEADER.replace("execute actions in model2", "execute actions in ecore")
                + "routine r() {\n  create {\n    val c = new ecore::EClassifier\n  }\n}\n");

    assertEquals("'EClassifier' is abstract, so it cannot be created", problem.message());
    assertEquals("Create one of its concrete subclasses: EClass, EDataType, EEnum", problem.hint());
  }

  @Test
  void leavesXbaseCodeOverridesStringsAndCommentsAlone() throws IOException {
    UploadedFile reactions =
        text(
            "valid.reactions",
            "import org.eclipse.emf.ecore.util.EcoreUtil\n"
                + HEADER
                + """
                import routines other using qualified names
                // model::Commented
                reaction other::Overridden {
                  after element model::Component deleted
                  call {
                    EcoreUtil::delete(affectedEObject)
                    val text = "model::InAString"
                    java.util.Collections::emptyList()
                  }
                }
                routine other.nested::name(model::Component component) {
                  update { component.name = '''model2::Rich''' }
                }
                reactions: other
                in reaction to changes in model and model2
                execute actions in model2
                """);

    processor.process(List.of(reactions), metamodels, projectRoot);
  }

  @Test
  void resolvesQualifiedNamesOfSubpackages() throws IOException {
    List<GeneratedMetamodel> nested =
        new MetamodelProcessor()
            .process(
                List.of(
                    text(
                        "outer.ecore",
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <ecore:EPackage xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" name="outer"
                            nsURI="http://example.org/outer" nsPrefix="outer">
                          <eClassifiers xsi:type="ecore:EClass" name="Top"/>
                          <eSubpackages name="inner" nsURI="http://example.org/outer/inner"
                              nsPrefix="inner">
                            <eClassifiers xsi:type="ecore:EClass" name="Deep"/>
                          </eSubpackages>
                        </ecore:EPackage>
                        """)),
                projectRoot.resolve("nested"));
    UploadedFile reactions =
        text(
            "qualified.reactions",
            """
            import "http://example.org/outer" as q using qualified names
            import "http://example.org/outer" as s
            reactions: names
            in reaction to changes in q
            execute actions in s
            reaction A { after element q::inner.Deep created call {} }
            reaction B { after element s::Deep created call {} }
            reaction C { after element q::Top created call {} }
            reaction D { after element q::Deep created call {} }
            """);

    VsumBuildException exception =
        assertThrows(
            VsumBuildException.class,
            () -> processor.process(List.of(reactions), nested, projectRoot));

    assertEquals(1, exception.getProblems().size());
    BuildProblem problem = exception.getProblems().get(0);
    assertEquals(9, problem.line());
    assertEquals("Did you mean 'inner.Deep'?", problem.hint());
  }

  private BuildProblem single(String reactions) {
    VsumBuildException exception =
        assertThrows(
            VsumBuildException.class,
            () ->
                processor.process(
                    List.of(text("checked.reactions", reactions)), metamodels, projectRoot));
    assertEquals(1, exception.getProblems().size(), exception.getMessage());
    return exception.getProblems().get(0);
  }
}
