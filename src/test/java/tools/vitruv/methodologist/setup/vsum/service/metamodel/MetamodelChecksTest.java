package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;

/**
 * Metamodels that EcoreValidator accepts, or reports confusingly, but whose generated Java code
 * does not compile. Each case was confirmed with a real build of the generated project.
 */
class MetamodelChecksTest {

  private static final String STRING =
      "eType=\"ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString\"";

  private final MetamodelProcessor processor = new MetamodelProcessor();

  @TempDir Path projectRoot;

  @Test
  void rejectsFeaturesThatDifferOnlyInCaseAlsoWhenInherited() {
    List<BuildProblem> problems =
        problems(
            """
            <eClassifiers xsi:type="ecore:EClass" name="Base">
              <eStructuralFeatures xsi:type="ecore:EAttribute" name="x" %1$s/>
            </eClassifiers>
            <eClassifiers xsi:type="ecore:EClass" name="Derived" eSuperTypes="#//Base">
              <eStructuralFeatures xsi:type="ecore:EAttribute" name="X" %1$s/>
            </eClassifiers>
            """
                .formatted(STRING));

    assertEquals(1, problems.size());
    BuildProblem problem = problems.get(0);
    assertEquals(
        "Derived.X: the features 'x' and 'X' differ only in upper/lower case or '_', so their"
            + " generated Java names clash",
        problem.message());
    assertEquals(10, problem.line());
    assertEquals("Rename one of them; features of the super types count as well", problem.hint());
  }

  @Test
  void rejectsClassesAndLiteralsThatDifferOnlyInCase() {
    List<BuildProblem> problems =
        problems(
            """
            <eClassifiers xsi:type="ecore:EClass" name="Entity"/>
            <eClassifiers xsi:type="ecore:EClass" name="entity"/>
            <eClassifiers xsi:type="ecore:EEnum" name="Kind">
              <eLiterals name="A"/>
              <eLiterals name="a" value="1"/>
            </eClassifiers>
            """);

    assertEquals(
        List.of(
            "entity: the classifiers 'Entity' and 'entity' differ only in upper/lower case or '_',"
                + " so their generated Java names clash",
            "Kind.a: the enum literals 'A' and 'a' differ only in upper/lower case or '_', so"
                + " their generated Java names clash"),
        problems.stream().map(BuildProblem::message).toList());
  }

  @Test
  void rejectsReservedJavaWordsAsClassNames() {
    List<BuildProblem> problems =
        problems(
            """
            <eClassifiers xsi:type="ecore:EClass" name="class"/>
            <eClassifiers xsi:type="ecore:EClass" name="record"/>
            <eClassifiers xsi:type="ecore:EClass" name="Holder">
              <eStructuralFeatures xsi:type="ecore:EAttribute" name="default" %s/>
            </eClassifiers>
            """
                .formatted(STRING));

    assertEquals(
        List.of(
            "class: 'class' is a reserved word in Java and cannot name a class",
            "record: 'record' is a reserved word in Java and cannot name a class"),
        problems.stream().map(BuildProblem::message).toList());
    assertEquals(
        "Rename it, e.g. to 'Class'; class names usually start with an upper-case letter",
        problems.get(0).hint());
    assertEquals(6, problems.get(0).line());
  }

  @Test
  void rejectsClassesNamedLikeTheGeneratedPackageInterfaces() {
    List<BuildProblem> problems =
        problems(
            """
            <eClassifiers xsi:type="ecore:EClass" name="ProbePackage"/>
            <eClassifiers xsi:type="ecore:EClass" name="ProbeFactory"/>
            """);

    assertEquals(2, problems.size());
    assertEquals(
        "ProbePackage: the class 'ProbePackage' has the name of the Java interface EMF generates"
            + " for the package 'probe'",
        problems.get(0).message());
    assertEquals(
        "Rename the class; EMF generates ProbePackage and ProbeFactory for the package",
        problems.get(0).hint());
  }

  @Test
  void explainsUntypedFeaturesWithoutObjectDumps() {
    List<BuildProblem> problems =
        problems(
            """
            <eClassifiers xsi:type="ecore:EClass" name="Thing">
              <eStructuralFeatures xsi:type="ecore:EAttribute" name="untyped"/>
            </eClassifiers>
            <eClassifiers xsi:type="ecore:EDataType" name="Custom"/>
            """);

    assertEquals(
        List.of(
            "Thing.untyped: The typed element must have a type",
            "Custom: the data type has no instance type name"),
        problems.stream().map(BuildProblem::message).toList());
    assertEquals(
        "Set the type, e.g. EString for an attribute or a class of the metamodels for a"
            + " reference",
        problems.get(0).hint());
    problems.forEach(problem -> assertFalse(problem.message().contains("org.eclipse")));
  }

  @Test
  void acceptsNamesThatEmfEscapes() {
    List<UploadedFile> uploads =
        List.of(
            ecore(
                """
                <eClassifiers xsi:type="ecore:EClass" name="Object">
                  <eStructuralFeatures xsi:type="ecore:EAttribute" name="class" %1$s/>
                  <eStructuralFeatures xsi:type="ecore:EAttribute" name="eContainer" %1$s/>
                </eClassifiers>
                <eClassifiers xsi:type="ecore:EEnum" name="Kind">
                  <eLiterals name="int"/>
                </eClassifiers>
                <eSubpackages name="new" nsURI="http://example.org/probe/new" nsPrefix="new">
                  <eClassifiers xsi:type="ecore:EClass" name="Inner"/>
                </eSubpackages>
                """
                    .formatted(STRING)));

    assertDoesNotThrow(() -> processor.process(uploads, projectRoot));
  }

  private List<BuildProblem> problems(String classifiers) {
    List<UploadedFile> uploads = List.of(ecore(classifiers));
    return assertThrows(VsumBuildException.class, () -> processor.process(uploads, projectRoot))
        .getProblems();
  }

  private static UploadedFile ecore(String classifiers) {
    return text(
        "probe.ecore",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <ecore:EPackage xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" name="probe"
            nsURI="http://example.org/probe" nsPrefix="probe">
        %s</ecore:EPackage>
        """
            .formatted(classifiers.indent(2)));
  }
}
