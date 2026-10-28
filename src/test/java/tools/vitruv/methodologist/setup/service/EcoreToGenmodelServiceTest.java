package tools.vitruv.methodologist.setup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.setup.exception.GenmodelException;
import tools.vitruv.methodologist.setup.model.service.EcoreToGenmodelService;

@DisplayName("EcoreToGenmodelService Tests")
class EcoreToGenmodelServiceTest {

  private static final String TEMPLATE_PLUGIN_ID = "tools.vitruv.methodologisttemplate.model";
  private static final String TEMPLATE_MODEL_DIRECTORY =
      "/tools.vitruv.methodologisttemplate.model/target/generated-sources/ecore";

  private EcoreToGenmodelService service;

  @BeforeEach
  void setUp() {
    service = new EcoreToGenmodelService();
  }

  private static byte[] ecore(String packageName, String nsUri, String classifiers) {
    String nsUriAttribute = nsUri == null ? "" : " nsURI=\"" + nsUri + "\"";
    return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<ecore:EPackage xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\"\n"
            + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" name=\""
            + packageName
            + "\""
            + nsUriAttribute
            + " nsPrefix=\""
            + packageName
            + "\">\n"
            + classifiers
            + "</ecore:EPackage>\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String eClass(String name) {
    return "  <eClassifiers xsi:type=\"ecore:EClass\" name=\""
        + name
        + "\">\n"
        + "    <eStructuralFeatures xsi:type=\"ecore:EAttribute\" name=\"name\""
        + " eType=\"ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString\"/>\n"
        + "  </eClassifiers>\n";
  }

  private String generate(byte[] ecore, String filename) {
    return new String(service.generateGenmodelFromEcore(ecore, filename), StandardCharsets.UTF_8);
  }

  private static int count(String text, String part) {
    return text.split(Pattern.quote(part), -1).length - 1;
  }

  @Test
  @DisplayName("Should use the Methodologist template plugin id, model directory and base package")
  void testUsesTemplateProjectConventions() {
    String genmodel =
        generate(
            ecore("family", "http://example.com/family", eClass("Person") + eClass("Address")),
            "family.ecore");

    assertEquals(TEMPLATE_PLUGIN_ID, EcoreToGenmodelService.MODEL_PLUGIN_ID);
    assertTrue(genmodel.contains("modelPluginID=\"" + TEMPLATE_PLUGIN_ID + "\""));
    assertTrue(genmodel.contains("modelDirectory=\"" + TEMPLATE_MODEL_DIRECTORY + "\""));
    assertTrue(genmodel.contains("basePackage=\"" + TEMPLATE_PLUGIN_ID + "\""));
  }

  @Test
  @DisplayName("Should put metamodels with different nsURIs into the same model project")
  void testDifferentNsUrisShareModelProject() {
    String first =
        generate(ecore("family", "http://example.com/family", eClass("Person")), "family.ecore");
    String second =
        generate(
            ecore("persons", "http:///org/example/persons/1.0.0", eClass("Employee")),
            "persons.ecore");

    assertTrue(first.contains("modelPluginID=\"" + TEMPLATE_PLUGIN_ID + "\""));
    assertTrue(second.contains("modelPluginID=\"" + TEMPLATE_PLUGIN_ID + "\""));
    assertTrue(second.contains("modelDirectory=\"" + TEMPLATE_MODEL_DIRECTORY + "\""));
  }

  @Test
  @DisplayName("Should still derive the classes, prefix and ecore reference from the ecore")
  void testStructureComesFromEcore() {
    String genmodel =
        generate(
            ecore("family", "http://example.com/family", eClass("Person") + eClass("Address")),
            "family.ecore");

    assertEquals(2, count(genmodel, "<genClasses"));
    assertTrue(genmodel.contains("ecoreClass=\"family.ecore#//Person\""));
    assertTrue(genmodel.contains("ecoreClass=\"family.ecore#//Address\""));
    assertTrue(genmodel.contains("prefix=\"Family\""));
    assertTrue(genmodel.contains("modelName=\"Family\""));
    assertTrue(genmodel.contains("<foreignModel>family.ecore</foreignModel>"));
  }

  @Test
  @DisplayName("Should not generate an XML resource for packages with ExtendedMetaData")
  void testNoXmlResourceForExtendedMetaData() {
    String annotatedClass =
        "  <eClassifiers xsi:type=\"ecore:EClass\" name=\"Person\">\n"
            + "    <eStructuralFeatures xsi:type=\"ecore:EAttribute\" name=\"name\""
            + " eType=\"ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString\">\n"
            + "      <eAnnotations"
            + " source=\"http:///org/eclipse/emf/ecore/util/ExtendedMetaData\">\n"
            + "        <details key=\"kind\" value=\"element\"/>\n"
            + "      </eAnnotations>\n"
            + "    </eStructuralFeatures>\n"
            + "  </eClassifiers>\n";

    String genmodel =
        generate(ecore("library", "http://example.com/library", annotatedClass), "library.ecore");

    assertFalse(genmodel.contains("resource=\"XML\""));
  }

  private static String eClassReferencing(String name, String type) {
    return "  <eClassifiers xsi:type=\"ecore:EClass\" name=\""
        + name
        + "\">\n"
        + "    <eStructuralFeatures xsi:type=\"ecore:EReference\" name=\"target\" eType=\""
        + type
        + "\"/>\n"
        + "  </eClassifiers>\n";
  }

  private Path writeEcore(Path dir, String filename, byte[] content) throws IOException {
    return Files.write(dir.resolve(filename), content);
  }

  @Test
  @DisplayName("Should reject an ecore without nsURI")
  void testMissingNsUri() {
    byte[] ecore = ecore("family", null, eClass("Person"));

    GenmodelException exception =
        assertThrows(GenmodelException.class, () -> generate(ecore, "family.ecore"));

    assertEquals("ECORE_NS_URI_MISSING", exception.getErrorCode());
  }

  @Test
  @DisplayName("Should reject content that is not an ecore")
  void testInvalidEcore() {
    byte[] html = "<html><body>not an ecore & more</body></html>".getBytes(StandardCharsets.UTF_8);

    GenmodelException exception =
        assertThrows(GenmodelException.class, () -> generate(html, "family.ecore"));

    assertEquals("ECORE_LOAD_ERROR", exception.getErrorCode());
  }
}
