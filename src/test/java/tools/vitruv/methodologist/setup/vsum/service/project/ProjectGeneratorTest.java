package tools.vitruv.methodologist.setup.vsum.service.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.vitruv.methodologist.setup.vsum.service.VsumBuildProperties;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;

class ProjectGeneratorTest {

  private static final List<String> POMS =
      List.of("pom.xml", "model/pom.xml", "consistency/pom.xml", "vsum/pom.xml");

  // Vitruvius is released per repository; each artifact group must use its own version property
  private static final Map<String, String> VERSION_PROPERTY_BY_PREFIX =
      Map.of(
          "tools.vitruv.change.", "${vitruv.change.version}",
          "tools.vitruv.framework.", "${vitruv.framework.version}",
          "tools.vitruv.dsls.", "${vitruv.dsls.version}",
          "tools.vitruv.server.", "${vitruv.server.version}");

  private static final List<GeneratedMetamodel> METAMODELS =
      List.of(
          new GeneratedMetamodel(
              "model",
              "http://vitruv.tools/methodologisttemplate/model",
              "model.ecore",
              "model.genmodel",
              List.of(
                  new GeneratedMetamodel.GeneratedPackage(
                      "http://vitruv.tools/methodologisttemplate/model",
                      "tools.vitruv.methodologisttemplate.model.model.ModelPackage"))));

  @TempDir Path projectRoot;

  @Test
  void generatesWellFormedPoms() throws Exception {
    generate(new VsumBuildProperties());

    for (String pom : POMS) {
      Document document = parse(projectRoot.resolve(pom));
      assertEquals("project", document.getDocumentElement().getTagName(), pom);
    }
  }

  @Test
  void versionsEveryVitruviusArtifactByItsReleaseUnit() throws Exception {
    generate(new VsumBuildProperties());

    List<String> checked = new ArrayList<>();
    for (String pom : POMS) {
      NodeList dependencies = parse(projectRoot.resolve(pom)).getElementsByTagName("dependency");
      for (int i = 0; i < dependencies.getLength(); i++) {
        Element dependency = (Element) dependencies.item(i);
        String artifactId = child(dependency, "artifactId");
        String version = child(dependency, "version");
        if (!"tools.vitruv".equals(child(dependency, "groupId")) || version == null) {
          continue;
        }
        String expected =
            VERSION_PROPERTY_BY_PREFIX.entrySet().stream()
                .filter(entry -> artifactId.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("unknown Vitruvius artifact " + artifactId));
        assertEquals(expected, version, pom + ": " + artifactId);
        checked.add(artifactId);
      }
    }
    // the managed dependencies of the root pom plus the Reactions compiler's plugin dependencies
    assertTrue(checked.contains("tools.vitruv.change.propagation"), checked.toString());
    assertTrue(checked.contains("tools.vitruv.framework.vsum"), checked.toString());
    assertTrue(checked.contains("tools.vitruv.dsls.reactions.language"), checked.toString());
    assertTrue(checked.contains("tools.vitruv.server.remote"), checked.toString());
  }

  @Test
  void writesTheConfiguredVersionsIntoTheProject() throws Exception {
    VsumBuildProperties properties = new VsumBuildProperties();
    properties.setParentVersion("9.0.0");
    properties.setVitruvChangeVersion("9.1.0");
    properties.setVitruvFrameworkVersion("9.2.0");
    properties.setVitruvDslsVersion("9.3.0");
    properties.setVitruvServerVersion("9.4.0");
    properties.setVitruvoclVersion("9.5.0");
    properties.setEmfCommonVersion("9.6.0");
    properties.setEmfEcoreVersion("9.7.0");
    properties.setAssemblyPluginVersion("9.8.0");

    generate(properties);

    Document rootPom = parse(projectRoot.resolve("pom.xml"));
    Element parent = (Element) rootPom.getElementsByTagName("parent").item(0);
    assertEquals("9.0.0", child(parent, "version"));
    assertEquals("9.1.0", property(rootPom, "vitruv.change.version"));
    assertEquals("9.2.0", property(rootPom, "vitruv.framework.version"));
    assertEquals("9.3.0", property(rootPom, "vitruv.dsls.version"));
    assertEquals("9.4.0", property(rootPom, "vitruv.server.version"));
    assertEquals("9.5.0", property(rootPom, "vitruvocl.version"));
    assertEquals("9.6.0", managedVersion(rootPom, "org.eclipse.emf.common"));
    assertEquals("9.7.0", managedVersion(rootPom, "org.eclipse.emf.ecore"));

    String vsumPom = Files.readString(projectRoot.resolve("vsum/pom.xml"));
    assertTrue(
        vsumPom.matches(
            "(?s).*<artifactId>maven-assembly-plugin</artifactId>\\s*<version>9.8.0</version>.*"),
        vsumPom);
  }

  @Test
  void leavesNoUnsplitVitruviusVersionBehind() throws Exception {
    generate(new VsumBuildProperties());

    for (String pom : POMS) {
      String content = Files.readString(projectRoot.resolve(pom));
      assertFalse(content.contains("${vitruv.version}"), pom);
      assertFalse(content.contains("[="), pom + " contains an unrendered template expression");
    }
  }

  private void generate(VsumBuildProperties properties) throws IOException {
    new ProjectGenerator(properties).generate(projectRoot, METAMODELS, List.of(), List.of());
  }

  private static Document parse(Path file) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
  }

  private static String property(Document pom, String name) {
    Element properties = (Element) pom.getElementsByTagName("properties").item(0);
    return child(properties, name);
  }

  private static String managedVersion(Document pom, String artifactId) {
    NodeList dependencies = pom.getElementsByTagName("dependency");
    for (int i = 0; i < dependencies.getLength(); i++) {
      Element dependency = (Element) dependencies.item(i);
      if (artifactId.equals(child(dependency, "artifactId"))) {
        return child(dependency, "version");
      }
    }
    throw new AssertionError("no dependency " + artifactId);
  }

  /** Text of the direct child element with the given name, or {@code null}. */
  private static String child(Element element, String name) {
    for (int i = 0; i < element.getChildNodes().getLength(); i++) {
      if (element.getChildNodes().item(i) instanceof Element child
          && child.getTagName().equals(name)) {
        return child.getTextContent().trim();
      }
    }
    return null;
  }
}
