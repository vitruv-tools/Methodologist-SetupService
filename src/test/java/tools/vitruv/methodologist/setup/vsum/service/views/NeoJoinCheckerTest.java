package tools.vitruv.methodologist.setup.vsum.service.views;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.IOException;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.common.types.TypesPackage;
import org.eclipse.xtext.xbase.XbasePackage;
import org.eclipse.xtext.xtype.XtypePackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.neojoin.ast.AstPackage;

class NeoJoinCheckerTest {

  private static final String MODEL_NS_URI = "http://vitruv.tools/methodologisttemplate/model";

  private static final List<GeneratedMetamodel> METAMODELS =
      new MetamodelProcessor().check(List.of(fixture("model.ecore"), fixture("model2.ecore")));

  private final NeoJoinChecker checker = new NeoJoinChecker();

  @Test
  void acceptsAValidQuery() {
    assertDoesNotThrow(() -> checker.check(List.of(fixture("components.nj")), METAMODELS));
  }

  @Test
  void acceptsNoViewDefinitions() {
    assertDoesNotThrow(() -> checker.check(List.of(), METAMODELS));
  }

  @Test
  void reportsSyntaxErrorsAtTheirLocation() {
    String view =
        """
        export components to "http://example.org/components"

        import "%s"

        from Component c
        create ComponentView {
            c.name
        """
            .formatted(MODEL_NS_URI);

    BuildProblem problem = onlyProblem(text("broken.nj", view));

    assertEquals("broken.nj", problem.file());
    assertNotNull(problem.line(), problem.toString());
    assertNotNull(problem.column(), problem.toString());
    assertNotNull(problem.excerpt(), problem.toString());
    assertTrue(problem.message().contains("'}'"), problem.message());
  }

  @Test
  void reportsImportsOfMetamodelsThatWereNotUploaded() {
    String view =
        """
        export components to "http://example.org/components"

        import "http://example.org/unknown"

        from Component c
        create ComponentView
        """;

    VsumBuildException exception = rejected(text("unknown.nj", view));

    BuildProblem problem = exception.getProblems().get(0);
    assertEquals(Source.VIEWS, problem.source());
    assertEquals("unknown.nj", problem.file());
    assertEquals(3, problem.line(), exception.getMessage());
    assertEquals(8, problem.column(), exception.getMessage());
    assertTrue(problem.message().contains("http://example.org/unknown"), problem.message());
    assertTrue(problem.excerpt().contains("import \"http://example.org/unknown\""));
  }

  @Test
  void reportsInvalidXbaseExpressions() {
    String view =
        fixture("components.nj").text().replace("supportedProtocols.size", "supportedProtocolz");

    BuildProblem problem = onlyProblem(text("components.nj", view));

    assertEquals(lineContaining(view, "supportedProtocolz"), problem.line());
    assertTrue(problem.message().contains("supportedProtocolz"), problem.message());
  }

  @Test
  void rejectsDuplicateExportNames() {
    UploadedFile first = view("a.nj", "components", "http://example.org/a");
    UploadedFile second = view("b.nj", "components", "http://example.org/b");

    BuildProblem problem = onlyProblem(first, second);

    assertEquals("b.nj", problem.file());
    assertEquals(1, problem.line());
    assertEquals(1, problem.column());
    assertEquals("Duplicate export name 'components' already declared in a.nj", problem.message());
    assertTrue(problem.excerpt().contains("export components to"), problem.excerpt());
  }

  @Test
  void rejectsDuplicateViewTypeNsUris() {
    UploadedFile first = view("a.nj", "first", "http://example.org/views");
    UploadedFile second = view("b.nj", "second", "http://example.org/views");

    BuildProblem problem = onlyProblem(first, second);

    assertEquals("b.nj", problem.file());
    assertEquals(1, problem.line());
    assertEquals(1, problem.column());
    assertEquals(
        "Duplicate view-type nsURI 'http://example.org/views' already declared in a.nj",
        problem.message());
    assertTrue(problem.excerpt().contains("export second to"), problem.excerpt());
  }

  @Test
  void resolvesImportsOfSubpackages() {
    EPackage root = ePackage("root", "http://example.org/root", "RootThing");
    EPackage sub = ePackage("sub", "http://example.org/root/sub", "SubThing");
    root.getESubpackages().add(sub);
    String view =
        """
        export things to "http://example.org/things"

        import "http://example.org/root/sub"

        from SubThing t
        create Thing {
            t.label
        }
        """;

    assertDoesNotThrow(() -> checker.check(List.of(text("things.nj", view)), metamodels(root)));
  }

  /**
   * Starts each test as in a fresh JVM, where the first NeoJoin validator registered would be kept
   * for all later checks.
   */
  @BeforeEach
  void forgetNeoJoinValidators() {
    List.of(
            AstPackage.eINSTANCE,
            XbasePackage.eINSTANCE,
            XtypePackage.eINSTANCE,
            TypesPackage.eINSTANCE)
        .forEach(EValidator.Registry.INSTANCE::remove);
  }

  /**
   * NeoJoin's validator resolves 'conforms to' with the metamodels of the check; a later request
   * must find its own metamodels, not only those of the first request.
   */
  @Test
  void validatesEachRequestAgainstItsOwnMetamodels() throws IOException {
    checker.check(List.of(fixture("components.nj")), METAMODELS);
    EPackage reference = ePackage("reference", "http://example.org/reference", "Thing");
    String view =
        """
        export things to "http://example.org/things"
        conforms to "http://example.org/reference"

        import "http://example.org/reference"

        from Thing t
        create Thing {
            t.label
        }
        """;

    assertDoesNotThrow(
        () -> checker.check(List.of(text("things.nj", view)), metamodels(reference)));
  }

  /** A later request must not see the metamodels of an earlier request. */
  @Test
  void doesNotSeeTheMetamodelsOfEarlierRequests() throws IOException {
    EPackage earlier = ePackage("earlier", "http://example.org/earlier", "Thing");
    String earlierView =
        """
        export things to "http://example.org/things"
        conforms to "http://example.org/earlier"

        import "http://example.org/earlier"

        from Thing t
        create Thing {
            t.label
        }
        """;
    checker.check(List.of(text("things.nj", earlierView)), metamodels(earlier));
    String laterView =
        """
        export components to "http://example.org/components"
        conforms to "http://example.org/earlier"

        import "%s"

        from Component c
        create ComponentView {
            c.name
        }
        """
            .formatted(MODEL_NS_URI);

    BuildProblem problem = onlyProblem(text("components.nj", laterView));

    assertEquals(2, problem.line());
    assertEquals(
        "Reference metamodel not found: http://example.org/earlier",
        problem.message(),
        problem.toString());
  }

  private BuildProblem onlyProblem(UploadedFile... views) {
    VsumBuildException exception = rejected(views);
    assertEquals(1, exception.getProblems().size(), exception.getMessage());
    BuildProblem problem = exception.getProblems().get(0);
    assertEquals(Source.VIEWS, problem.source());
    return problem;
  }

  private VsumBuildException rejected(UploadedFile... views) {
    VsumBuildException exception =
        assertThrows(VsumBuildException.class, () -> checker.check(List.of(views), METAMODELS));
    assertEquals(VsumErrorCodes.INPUT_ERROR, exception.getErrorCode(), exception.getMessage());
    return exception;
  }

  private static UploadedFile view(String fileName, String exportName, String nsUri) {
    return text(
        fileName,
        """
        export %s to "%s"

        import "%s"

        from Component c
        create ComponentView {
            c.name
        }
        """
            .formatted(exportName, nsUri, MODEL_NS_URI));
  }

  private static EPackage ePackage(String name, String nsUri, String className) {
    EPackage ePackage = EcoreFactory.eINSTANCE.createEPackage();
    ePackage.setName(name);
    ePackage.setNsPrefix(name);
    ePackage.setNsURI(nsUri);
    EClass eClass = EcoreFactory.eINSTANCE.createEClass();
    eClass.setName(className);
    EAttribute label = EcoreFactory.eINSTANCE.createEAttribute();
    label.setName("label");
    label.setEType(EcorePackage.Literals.ESTRING);
    eClass.getEStructuralFeatures().add(label);
    ePackage.getEClassifiers().add(eClass);
    return ePackage;
  }

  /** Places the package in a resource, as the metamodel processor does with the uploads. */
  private static List<GeneratedMetamodel> metamodels(EPackage ePackage) {
    new ResourceImpl(URI.createURI(ePackage.getName() + ".ecore")).getContents().add(ePackage);
    return List.of(
        new GeneratedMetamodel(
            ePackage.getName(),
            ePackage.getNsURI(),
            ePackage.getName() + ".ecore",
            ePackage.getName() + ".genmodel",
            List.of(),
            ePackage));
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
}
