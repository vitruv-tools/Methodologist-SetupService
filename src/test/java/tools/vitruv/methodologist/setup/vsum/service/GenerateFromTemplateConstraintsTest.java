package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.config.GeneratedProjectVersions;

/** Tests the Vitruvius versions and the constraint wiring of generated projects. */
class GenerateFromTemplateConstraintsTest {
  private static final String PACKAGE_NAME = "tools.vitruv.methodologisttemplate";

  @TempDir Path tempDir;

  private GenerateFromTemplate generator;

  @BeforeEach
  void setUp() {
    GeneratedProjectVersions versions = new GeneratedProjectVersions();
    versions.setParent("9.0.0");
    versions.setChange("9.1.0");
    versions.setFramework("9.2.0-SNAPSHOT");
    versions.setDsls("9.3.0");
    versions.setServer("9.4.0-SNAPSHOT");
    versions.setVitruvocl("9.5.0-SNAPSHOT");
    generator = new GenerateFromTemplate(versions);
  }

  @Test
  @DisplayName("root pom uses the configured versions and the snapshot repository")
  void rootPomUsesConfiguredVersions() throws Exception {
    Path target = tempDir.resolve("pom.xml");

    generator.generateRootPom(target.toFile(), PACKAGE_NAME);

    String content = Files.readString(target);
    assertTrue(content.contains("<artifactId>parent</artifactId>\n    <version>9.0.0</version>"));
    assertTrue(content.contains("<vitruv-change.version>9.1.0</vitruv-change.version>"));
    assertTrue(content.contains("<vitruv.version>9.2.0-SNAPSHOT</vitruv.version>"));
    assertTrue(content.contains("<vitruv-dsls.version>9.3.0</vitruv-dsls.version>"));
    assertTrue(content.contains("<vitruv-server.version>9.4.0-SNAPSHOT</vitruv-server.version>"));
    assertTrue(content.contains("<vitruvocl.version>9.5.0-SNAPSHOT</vitruvocl.version>"));
    assertTrue(content.contains("https://central.sonatype.com/repository/maven-snapshots/"));
    assertFalse(content.contains("3.2.3"));
  }

  @Test
  @DisplayName("consistency pom uses the configured Reactions version")
  void consistencyPomUsesConfiguredDslsVersion() throws Exception {
    Path target = tempDir.resolve("consistency/pom.xml");

    generator.generateConsistencyPom(target.toFile(), PACKAGE_NAME);

    String content = Files.readString(target);
    assertTrue(content.contains("<version>${vitruv-dsls.version}</version>"));
    assertFalse(content.contains("3.1.0"));
  }

  @Test
  @DisplayName("vsum pom depends on VitruvOCL only if the project has constraints")
  void vsumPomDependsOnVitruvOclOnlyWithConstraints() throws Exception {
    Path without = tempDir.resolve("without/pom.xml");
    Path with = tempDir.resolve("with/pom.xml");

    generator.generateVsumPom(without.toFile(), PACKAGE_NAME);
    generator.generateVsumPom(with.toFile(), PACKAGE_NAME, true);

    assertFalse(Files.readString(without).contains("vitruvocl-language"));
    String content = Files.readString(with);
    assertTrue(content.contains("<artifactId>vitruvocl-language</artifactId>"));
    assertFalse(content.contains("3.2.3"));
  }

  @Test
  @DisplayName("VSUM example registers the constraints from the classpath")
  void vsumExampleRegistersConstraints() throws Exception {
    Path target = tempDir.resolve("VSUMExample.java");

    generator.generateVsumExample(
        target.toFile(),
        PACKAGE_NAME,
        List.of("Alpha"),
        List.of("mir.reactions.a.ASpecification"),
        List.of("constraints/first.ocl", "constraints/second.ocl"));

    String content = Files.readString(target);
    assertTrue(
        content.contains("import tools.vitruv.dsls.vitruvocl.vitruv.VitruvOclConstraintChecker;"));
    assertTrue(
        content.contains(
            ".withConstraintChecker(VitruvOclConstraintChecker.fromClasspath("
                + "\"constraints/first.ocl\", \"constraints/second.ocl\"))"));
  }

  @Test
  @DisplayName("VSUM example without constraints does not use VitruvOCL")
  void vsumExampleWithoutConstraints() throws Exception {
    Path target = tempDir.resolve("VSUMExample.java");

    generator.generateVsumExample(
        target.toFile(), PACKAGE_NAME, List.of("Alpha"), List.of("mir.reactions.a.A"), List.of());

    assertFalse(Files.readString(target).contains("VitruvOcl"));
  }
}
