package tools.vitruv.methodologist.setup.vsum.service.project;

import java.nio.file.Path;

/**
 * Names and paths of the generated VSUM project. The layout mirrors the <a
 * href="https://github.com/vitruv-tools/Methodologist-Template">Methodologist Template</a> and is
 * the single source of truth for every generated artifact, so that genmodels, the MWE2 workflow,
 * {@code plugin.xml}, POMs, and the VSUM main class always agree with each other.
 */
public final class ProjectLayout {

  /** Maven groupId of the generated project. */
  public static final String GROUP_ID = "tools.vitruv";

  /** Artifact id of the root project; also the base Java package. */
  public static final String ROOT_ARTIFACT_ID = "tools.vitruv.methodologisttemplate";

  /** Version of the generated project. */
  public static final String VERSION = "0.1.0-SNAPSHOT";

  /**
   * Artifact id of the model module. It is also the Eclipse project name that {@code
   * platform:/resource} URIs in genmodels and the MWE2 workflow resolve against.
   */
  public static final String MODEL_ARTIFACT_ID = ROOT_ARTIFACT_ID + ".model";

  /** Artifact id of the consistency module. */
  public static final String CONSISTENCY_ARTIFACT_ID = ROOT_ARTIFACT_ID + ".consistency";

  /** Artifact id of the VSUM module. */
  public static final String VSUM_ARTIFACT_ID = ROOT_ARTIFACT_ID + ".vsum";

  /** Base package under which EMF generates the metamodel code. */
  public static final String MODEL_BASE_PACKAGE = MODEL_ARTIFACT_ID;

  /** Java package of the generated VSUM main and test classes. */
  public static final String VSUM_PACKAGE = VSUM_ARTIFACT_ID;

  /** Simple name of the generated VSUM main class. */
  public static final String VSUM_MAIN_CLASS = "VSUMExample";

  /** Package (as resource path) in which reactions and constraints are placed. */
  public static final String CONSISTENCY_RESOURCE_PACKAGE =
      CONSISTENCY_ARTIFACT_ID.replace('.', '/');

  /** Ecore and genmodel directory, relative to the model module. */
  public static final String ECORE_DIRECTORY = "src/main/ecore";

  /** Directory receiving EMF generated code, relative to the model module. */
  public static final String GENERATED_ECORE_SOURCES = "target/generated-sources/ecore";

  /** File name of the executable VSUM jar produced by the build. */
  public static final String VSUM_JAR_FILENAME =
      VSUM_ARTIFACT_ID + "-" + VERSION + "-jar-with-dependencies.jar";

  private ProjectLayout() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Returns the model module directory.
   *
   * @param root project root
   * @return the model module directory
   */
  public static Path modelModule(Path root) {
    return root.resolve("model");
  }

  /**
   * Returns the directory holding ecore and genmodel files.
   *
   * @param root project root
   * @return the ecore directory
   */
  public static Path ecoreDirectory(Path root) {
    return modelModule(root).resolve(ECORE_DIRECTORY);
  }

  /**
   * Returns the consistency module directory.
   *
   * @param root project root
   * @return the consistency module directory
   */
  public static Path consistencyModule(Path root) {
    return root.resolve("consistency");
  }

  /**
   * Returns the directory holding reactions files.
   *
   * @param root project root
   * @return the reactions directory
   */
  public static Path reactionsDirectory(Path root) {
    return consistencyModule(root)
        .resolve("src/main/reactions")
        .resolve(CONSISTENCY_RESOURCE_PACKAGE);
  }

  /**
   * Returns the directory holding VitruvOCL constraint files. It is a resource root of the
   * consistency module, so constraints are available on the VSUM classpath.
   *
   * @param root project root
   * @return the constraints directory
   */
  public static Path constraintsDirectory(Path root) {
    return consistencyModule(root)
        .resolve("src/main/constraints")
        .resolve(CONSISTENCY_RESOURCE_PACKAGE);
  }

  /**
   * Returns the directory holding view definitions.
   *
   * @param root project root
   * @return the view definitions directory
   */
  public static Path viewsDirectory(Path root) {
    return root.resolve("views/src/main/neojoin");
  }

  /**
   * Returns the VSUM module directory.
   *
   * @param root project root
   * @return the VSUM module directory
   */
  public static Path vsumModule(Path root) {
    return root.resolve("vsum");
  }

  /**
   * Returns the location of the executable VSUM jar after a successful build.
   *
   * @param root project root
   * @return the jar path
   */
  public static Path vsumJar(Path root) {
    return vsumModule(root).resolve("target").resolve(VSUM_JAR_FILENAME);
  }
}
