package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.config.MetamodelLocation;
import tools.vitruv.methodologist.setup.config.VitruvConfiguration;
import tools.vitruv.methodologist.setup.exception.MissingModelException;

/**
 * Unit tests for {@link GenerateFromTemplate}.
 *
 * <p>The templates are loaded from the production classpath ({@code /templates}), so every test
 * exercises the real FreeMarker rendering pipeline against the bundled template files.
 */
@DisplayName("GenerateFromTemplate Tests")
class GenerateFromTemplateTest {

  private static final String PACKAGE_NAME = "tools.vitruv.generated";

  @TempDir Path tempDir;

  private GenerateFromTemplate generator;

  /** Creates a fresh generator instance before each test. */
  @BeforeEach
  void setUp() {
    generator = new GenerateFromTemplate();
  }

  /**
   * Reads the rendered file content for a target produced by the generator.
   *
   * @param target the rendered file
   * @return the file content as a string
   * @throws IOException when the file cannot be read
   */
  private String read(Path target) throws IOException {
    return Files.readString(target);
  }

  @Nested
  @DisplayName("generateRootPom")
  class GenerateRootPom {

    /** Verifies the root pom is rendered and contains the supplied package name. */
    @Test
    @DisplayName("Should render root pom containing the package name")
    void rendersRootPom() throws Exception {
      Path target = tempDir.resolve("pom.xml");

      generator.generateRootPom(target.toFile(), PACKAGE_NAME);

      assertTrue(Files.exists(target));
      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies a surrounding-whitespace package name is trimmed before rendering. */
    @Test
    @DisplayName("Should trim the package name before rendering")
    void trimsPackageName() throws Exception {
      Path target = tempDir.resolve("pom.xml");

      generator.generateRootPom(target.toFile(), "  " + PACKAGE_NAME + "  ");

      String content = read(target);
      assertTrue(content.contains("<artifactId>" + PACKAGE_NAME + "</artifactId>"));
    }

    /** Verifies a {@code null} package name is rejected with a {@link MissingModelException}. */
    @Test
    @DisplayName("Should reject a null package name")
    void rejectsNullPackageName() {
      File target = tempDir.resolve("pom.xml").toFile();

      assertThrows(
          MissingModelException.class, () -> generator.generateRootPom(target, null));
    }

    /** Verifies an empty package name is rejected with a {@link MissingModelException}. */
    @Test
    @DisplayName("Should reject an empty package name")
    void rejectsEmptyPackageName() {
      File target = tempDir.resolve("pom.xml").toFile();

      assertThrows(MissingModelException.class, () -> generator.generateRootPom(target, ""));
    }

    /** Verifies parent directories are created automatically for a nested target path. */
    @Test
    @DisplayName("Should create missing parent directories")
    void createsParentDirectories() throws Exception {
      Path target = tempDir.resolve("nested/deep/pom.xml");

      generator.generateRootPom(target.toFile(), PACKAGE_NAME);

      assertTrue(Files.exists(target));
    }
  }

  @Nested
  @DisplayName("single package-name pom generators")
  class SinglePackageNamePoms {

    /** Verifies the vsum pom is rendered with the package name. */
    @Test
    @DisplayName("Should render vsum pom")
    void rendersVsumPom() throws Exception {
      Path target = tempDir.resolve("vsum/pom.xml");

      generator.generateVsumPom(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies the p2wrappers pom is rendered with the package name. */
    @Test
    @DisplayName("Should render p2wrappers pom")
    void rendersP2WrappersPom() throws Exception {
      Path target = tempDir.resolve("p2wrappers/pom.xml");

      generator.generateP2WrappersPom(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies the javautils pom is rendered with the package name. */
    @Test
    @DisplayName("Should render javautils pom")
    void rendersJavaUtilsPom() throws Exception {
      Path target = tempDir.resolve("javautils/pom.xml");

      generator.generateJavaUtilsPom(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies the xannotations pom is rendered with the package name. */
    @Test
    @DisplayName("Should render xannotations pom")
    void rendersXAnnotationsPom() throws Exception {
      Path target = tempDir.resolve("xannotations/pom.xml");

      generator.generateXAnnotationsPom(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies the emfutils pom is rendered with the package name. */
    @Test
    @DisplayName("Should render emfutils pom")
    void rendersEmfUtilsPom() throws Exception {
      Path target = tempDir.resolve("emfutils/pom.xml");

      generator.generateEMFUtilsPom(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies the model pom is rendered with the package name. */
    @Test
    @DisplayName("Should render model pom")
    void rendersModelPom() throws Exception {
      Path target = tempDir.resolve("model/pom.xml");

      generator.generateModelPom(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies the consistency pom is rendered with the package name. */
    @Test
    @DisplayName("Should render consistency pom")
    void rendersConsistencyPom() throws Exception {
      Path target = tempDir.resolve("consistency/pom.xml");

      generator.generateConsistencyPom(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }

    /** Verifies the vsum test class is rendered with the package declaration. */
    @Test
    @DisplayName("Should render vsum test class")
    void rendersVsumTest() throws Exception {
      Path target = tempDir.resolve("vsum/src/test/java/VSUMExampleTest.java");

      generator.generateVsumTest(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains("package " + PACKAGE_NAME + ".vsum;"));
    }

    /** Verifies the Eclipse {@code .project} file is rendered with the package name. */
    @Test
    @DisplayName("Should render project file")
    void rendersProjectFile() throws Exception {
      Path target = tempDir.resolve("model/.project");

      generator.generateProjectFile(target.toFile(), PACKAGE_NAME);

      assertTrue(read(target).contains(PACKAGE_NAME));
    }
  }

  @Nested
  @DisplayName("generateVsumExample")
  class GenerateVsumExample {

    /** Verifies the example renders one view type entry per supplied model name. */
    @Test
    @DisplayName("Should render a view type for every model name")
    void rendersViewTypesPerModel() throws Exception {
      Path target = tempDir.resolve("vsum/src/main/java/VSUMExample.java");

      generator.generateVsumExample(target.toFile(), PACKAGE_NAME, List.of("Alpha", "Beta"));

      String content = read(target);
      assertTrue(content.contains("package " + PACKAGE_NAME + ".vsum;"));
      assertTrue(content.contains("createIdentityMappingViewType(\"Alpha\")"));
      assertTrue(content.contains("createIdentityMappingViewType(\"Beta\")"));
    }

    /** Verifies the example still renders when no model names are supplied. */
    @Test
    @DisplayName("Should render with an empty model list")
    void rendersWithEmptyModelList() throws Exception {
      Path target = tempDir.resolve("vsum/src/main/java/VSUMExample.java");

      generator.generateVsumExample(target.toFile(), PACKAGE_NAME, List.of());

      assertTrue(read(target).contains("class VSUMExample"));
    }
  }

  @Nested
  @DisplayName("generateMwe2")
  class GenerateMwe2 {

    /** Verifies the mwe2 workflow embeds model, package and target directory information. */
    @Test
    @DisplayName("Should render a component per metamodel location")
    void rendersComponentsPerModel() throws Exception {
      Path target = tempDir.resolve("model/workflow/generate.mwe2");
      VitruvConfiguration config = configuration();
      List<MetamodelLocation> models = List.of(metamodelLocation("src/main/ecore"));

      generator.generateMwe2(target.toFile(), models, config);

      String content = read(target);
      assertTrue(content.contains("model.genmodel"));
      assertTrue(content.contains(PACKAGE_NAME + ".model"));
      assertTrue(content.contains("src/main/ecore"));
    }

    /** Verifies backslashes and duplicated slashes in the model directory are normalized. */
    @Test
    @DisplayName("Should normalize the model directory separators")
    void normalizesModelDirectory() throws Exception {
      Path target = tempDir.resolve("model/workflow/generate.mwe2");
      VitruvConfiguration config = configuration();
      List<MetamodelLocation> models = List.of(metamodelLocation("a\\b//c"));

      generator.generateMwe2(target.toFile(), models, config);

      String content = read(target);
      assertTrue(content.contains("a/b/c"));
      assertFalse(content.contains("a\\b"));
    }
  }

  @Nested
  @DisplayName("generatePlugin")
  class GeneratePlugin {

    /** Verifies the plugin descriptor embeds the model URI, package and capitalized model name. */
    @Test
    @DisplayName("Should render an extension per metamodel location")
    void rendersExtensionPerModel() throws Exception {
      Path target = tempDir.resolve("model/plugin.xml");
      VitruvConfiguration config = configuration();
      List<MetamodelLocation> models = List.of(metamodelLocation("src/main/ecore"));

      generator.generatePlugin(target.toFile(), config, models);

      String content = read(target);
      assertTrue(content.contains("http://example.org/model"));
      assertTrue(content.contains(PACKAGE_NAME + ".model.model.ModelPackage"));
      assertTrue(content.contains("src/main/ecore/model.genmodel"));
    }

    /** Verifies the plugin descriptor renders without extensions when no models are supplied. */
    @Test
    @DisplayName("Should render an empty plugin descriptor")
    void rendersEmptyPlugin() throws Exception {
      Path target = tempDir.resolve("model/plugin.xml");

      generator.generatePlugin(target.toFile(), configuration(), List.of());

      assertTrue(read(target).contains("<plugin>"));
    }
  }

  @Nested
  @DisplayName("error handling")
  class ErrorHandling {

    /** Verifies an {@link IOException} is raised when the target path is a directory. */
    @Test
    @DisplayName("Should fail when the target path is a directory")
    void failsWhenTargetIsDirectory() throws Exception {
      Path directoryTarget = Files.createDirectory(tempDir.resolve("collision"));

      assertThrows(
          IOException.class,
          () -> generator.generateVsumPom(directoryTarget.toFile(), PACKAGE_NAME));
    }
  }

  /**
   * Builds a configuration with a local path and package name suitable for the mwe2 and plugin
   * generators.
   *
   * @return a populated configuration
   */
  private VitruvConfiguration configuration() {
    VitruvConfiguration config = new VitruvConfiguration();
    config.setLocalPath(tempDir);
    config.setPackageName(PACKAGE_NAME);
    return config;
  }

  /**
   * Builds a metamodel location whose genmodel file name is {@code model.genmodel}.
   *
   * @param modelDirectory the model directory used by the mwe2 generator
   * @return a metamodel location
   */
  private MetamodelLocation metamodelLocation(String modelDirectory) {
    File metamodel = tempDir.resolve("model.ecore").toFile();
    File genmodel = tempDir.resolve("model.genmodel").toFile();
    return new MetamodelLocation(metamodel, genmodel, "http://example.org/model", modelDirectory);
  }

  /** Verifies the placeholder for the unused private field assertion utility. */
  @Test
  @DisplayName("Should expose a no-argument constructor")
  void exposesNoArgConstructor() {
    assertEquals(GenerateFromTemplate.class, new GenerateFromTemplate().getClass());
  }
}
