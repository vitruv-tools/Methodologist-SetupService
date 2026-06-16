package tools.vitruv.methodologist.setup.vsum.service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.config.VitruvConfiguration;
import tools.vitruv.methodologist.setup.exception.MissingModelException;

/** Business service for building and packaging VSUM projects from uploaded model files. */
@Slf4j
@Service
@RequiredArgsConstructor
public class VsumService {

  private final GenerateFromTemplate generateFromTemplate;

  /**
   * Generates a full VSUM project, builds it with Maven, and returns the project archive as bytes.
   *
   * @param modelFiles paired metamodel/genmodel files
   * @param reactionFiles optional reaction files
   * @param metamodelNamespaceMap map of model names to nsURIs
   * @return zip archive bytes containing generated project and build artifacts
   * @throws IOException when file IO or build execution fails
   * @throws InterruptedException when the build process is interrupted
   * @throws MissingModelException when the model configuration is invalid
   */
  public byte[] generateProjectArchive(
      List<ModelFiles> modelFiles,
      List<File> reactionFiles,
      Map<String, String> metamodelNamespaceMap)
      throws IOException, InterruptedException, MissingModelException {
    validateInputs(modelFiles, reactionFiles);

    Path workspace = Files.createTempDirectory("vitruv-cli-project-");
    try {
      VitruvConfiguration configuration = new VitruvConfiguration();
      configuration.setLocalPath(workspace);

      List<ModelFiles> copiedModelFiles = copyModelFiles(workspace, modelFiles);
      configuration.setMetaModelLocations(buildModelLocations(copiedModelFiles));

      List<Path> copiedReactionFiles = copyReactionFiles(workspace, reactionFiles);
      configuration.setReactionLocations(copiedReactionFiles);

      generateProjectFiles(configuration);
      runMavenBuild(workspace, metamodelNamespaceMap);
      return zipDirectory(workspace);
    } finally {
      deleteRecursively(workspace);
    }
  }

  /**
   * Generates a full VSUM project, builds it with Maven, and returns the project archive as bytes.
   *
   * @param modelFiles paired metamodel/genmodel files
   * @param reactionFiles optional reaction files
   * @return zip archive bytes containing generated project and build artifacts
   * @throws IOException when file IO or build execution fails
   * @throws InterruptedException when the build process is interrupted
   * @throws MissingModelException when the model configuration is invalid
   */
  public byte[] generateProjectArchive(List<ModelFiles> modelFiles, List<File> reactionFiles)
      throws IOException, InterruptedException, MissingModelException {
    return generateProjectArchive(modelFiles, reactionFiles, Map.of());
  }

  /**
   * Validates that the supplied model and reaction files are present and usable.
   *
   * @param modelFiles paired metamodel/genmodel files; must contain at least one pair
   * @param reactionFiles optional reaction files; may be {@code null}
   * @throws IllegalArgumentException when no model pairs are provided or any file is invalid
   */
  private void validateInputs(List<ModelFiles> modelFiles, List<File> reactionFiles) {
    if (modelFiles == null || modelFiles.isEmpty()) {
      throw new IllegalArgumentException("At least one metamodel/genmodel pair is required.");
    }

    for (ModelFiles pair : modelFiles) {
      validateFile(pair.metamodelFile(), "metamodelFile");
      validateFile(pair.genmodelFile(), "genmodelFile");
    }

    if (reactionFiles != null) {
      for (File reactionFile : reactionFiles) {
        validateFile(reactionFile, "reactionFile");
      }
    }
  }

  /**
   * Validates that a single file is non-null and refers to an existing regular file.
   *
   * @param file the file to validate
   * @param label human-readable name used in error messages
   * @throws IllegalArgumentException when the file is null, missing, or not a regular file
   */
  private void validateFile(File file, String label) {
    if (file == null) {
      throw new IllegalArgumentException(label + " must not be null.");
    }
    if (!file.exists()) {
      throw new IllegalArgumentException(label + " does not exist: " + file.getAbsolutePath());
    }
    if (!file.isFile()) {
      throw new IllegalArgumentException(label + " is not a file: " + file.getAbsolutePath());
    }
  }

  /**
   * Copies each metamodel/genmodel pair into the workspace's ecore source directory.
   *
   * @param workspace root directory of the generated project
   * @param modelFiles paired metamodel/genmodel files to copy
   * @return the copied pairs pointing at their new locations within the workspace
   * @throws IOException when creating the target directory or copying a file fails
   */
  private List<ModelFiles> copyModelFiles(Path workspace, List<ModelFiles> modelFiles)
      throws IOException {
    Path targetDirectory = workspace.resolve("model/src/main/ecore");
    Files.createDirectories(targetDirectory);

    List<ModelFiles> copied = new ArrayList<>();
    for (ModelFiles pair : modelFiles) {
      File copiedMetamodel = copyFile(pair.metamodelFile(), targetDirectory);
      File copiedGenmodel = copyFile(pair.genmodelFile(), targetDirectory);
      copied.add(new ModelFiles(copiedMetamodel, copiedGenmodel));
    }
    return copied;
  }

  /**
   * Copies any reaction files into the workspace's reactions source directory.
   *
   * @param workspace root directory of the generated project
   * @param reactionFiles reaction files to copy; may be {@code null} or empty
   * @return the paths of the copied reaction files, or an empty list when none were provided
   * @throws IOException when creating the target directory or copying a file fails
   */
  private List<Path> copyReactionFiles(Path workspace, List<File> reactionFiles)
      throws IOException {
    if (reactionFiles == null || reactionFiles.isEmpty()) {
      return List.of();
    }

    Path targetDirectory = workspace.resolve("consistency/src/main/reactions");
    Files.createDirectories(targetDirectory);

    List<Path> copied = new ArrayList<>();
    for (File reactionFile : reactionFiles) {
      copied.add(copyFile(reactionFile, targetDirectory).toPath());
    }
    return copied;
  }

  /**
   * Copies a single file into the target directory, overwriting any existing file with the same
   * name.
   *
   * @param source the file to copy
   * @param targetDirectory the directory to copy the file into
   * @return the copied file at its new location
   * @throws IOException when the copy operation fails
   */
  private File copyFile(File source, Path targetDirectory) throws IOException {
    Path target = targetDirectory.resolve(source.getName());
    Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
    return target.toFile();
  }

  /**
   * Builds the metamodel locations string consumed by the Vitruv configuration, pairing each
   * metamodel with its genmodel.
   *
   * @param copiedModelFiles the copied metamodel/genmodel pairs
   * @return a string of comma-separated metamodel/genmodel paths, with pairs separated by
   *     semicolons
   */
  private String buildModelLocations(List<ModelFiles> copiedModelFiles) {
    return copiedModelFiles.stream()
        .map(
            pair ->
                pair.metamodelFile().getAbsolutePath()
                    + ","
                    + pair.genmodelFile().getAbsolutePath())
        .collect(Collectors.joining(";"));
  }

  /**
   * Generates all project files from templates, including the POMs for each module, the VSUM
   * example and test sources, the Eclipse project descriptor, the MWE2 workflow, and the plugin
   * descriptor.
   *
   * @param configuration the Vitruv configuration describing the project to generate
   * @throws IOException when writing a generated file fails
   * @throws MissingModelException when the model configuration is invalid
   */
  private void generateProjectFiles(VitruvConfiguration configuration)
      throws IOException, MissingModelException {
    generateFromTemplate.generateRootPom(
        new File((configuration.getLocalPath() + "/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateConsistencyPom(
        new File((configuration.getLocalPath() + "/consistency/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateModelPom(
        new File((configuration.getLocalPath() + "/model/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateVsumPom(
        new File((configuration.getLocalPath() + "/vsum/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateP2WrappersPom(
        new File((configuration.getLocalPath() + "/p2wrappers/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateJavaUtilsPom(
        new File((configuration.getLocalPath() + "/p2wrappers/javautils/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateXAnnotationsPom(
        new File(
            (configuration.getLocalPath() + "/p2wrappers/activextendannotations/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateEMFUtilsPom(
        new File((configuration.getLocalPath() + "/p2wrappers/emfutils/pom.xml").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateVsumExample(
        new File((configuration.getLocalPath() + "/vsum/src/main/java/VSUMExample.java").trim()),
        configuration.getPackageName(),
        configuration.getModelNames());
    generateFromTemplate.generateVsumTest(
        new File(
            (configuration.getLocalPath() + "/vsum/src/test/java/VSUMExampleTest.java").trim()),
        configuration.getPackageName());
    generateFromTemplate.generateProjectFile(
        new File((configuration.getLocalPath() + "/model/.project").trim()),
        configuration.getPackageName());

    File workflow =
        new File((configuration.getLocalPath() + "/model/workflow/generate.mwe2").trim());
    configuration.setWorkflow(workflow);
    generateFromTemplate.generateMwe2(
        workflow, configuration.getMetaModelLocations(), configuration);
    generateFromTemplate.generatePlugin(
        new File((configuration.getLocalPath() + "/model/plugin.xml").trim()),
        configuration,
        configuration.getMetaModelLocations());
  }

  /**
   * Runs the Maven build for the generated project without any metamodel namespace properties.
   *
   * @param projectRoot root directory of the generated project
   * @throws IOException when the build fails or its output cannot be read
   * @throws InterruptedException when the build process is interrupted while waiting
   */
  protected void runMavenBuild(Path projectRoot) throws IOException, InterruptedException {
    runMavenBuild(projectRoot, Map.of());
  }

  /**
   * Runs the Maven {@code clean verify} build for the generated project, passing metamodel
   * namespace information as system properties, and fails when the build exits with a non-zero
   * status.
   *
   * @param projectRoot root directory of the generated project
   * @param metamodelNamespaceMap map of model names to nsURIs passed as build properties
   * @throws IOException when the build fails or its output cannot be read
   * @throws InterruptedException when the build process is interrupted while waiting
   */
  protected void runMavenBuild(Path projectRoot, Map<String, String> metamodelNamespaceMap)
      throws IOException, InterruptedException {
    ProcessBuilder processBuilder = createMavenProcessBuilder(projectRoot, metamodelNamespaceMap);
    Process process = processBuilder.start();

    StringWriter output = new StringWriter();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      reader.transferTo(output);
    }

    process.waitFor();
    if (process.exitValue() != 0) {
      throw new IOException(
          "Maven build failed with exit code " + process.exitValue() + ". Output: " + output);
    }
  }

  /**
   * Creates the {@link ProcessBuilder} for the Maven build, appending each metamodel namespace
   * entry as a {@code -Dmetamodel.<name>.nsuri=<value>} system property and redirecting the error
   * stream into standard output.
   *
   * @param projectRoot root directory of the generated project, used as the working directory
   * @param metamodelNamespaceMap map of model names to nsURIs to append as system properties
   * @return a configured process builder ready to be started
   */
  private ProcessBuilder createMavenProcessBuilder(
      Path projectRoot, Map<String, String> metamodelNamespaceMap) {
    ProcessBuilder processBuilder = new ProcessBuilder("mvn", "clean", "verify", "-DskipTests");

    // Pass metamodel namespace information as system properties
    if (metamodelNamespaceMap != null && !metamodelNamespaceMap.isEmpty()) {
      for (var entry : metamodelNamespaceMap.entrySet()) {
        String propName = "metamodel." + entry.getKey() + ".nsuri";
        String propValue = "-D" + propName + "=" + entry.getValue();
        processBuilder.command().add(propValue);
      }
    }

    processBuilder.directory(projectRoot.toFile());
    processBuilder.redirectErrorStream(true);
    return processBuilder;
  }

  /**
   * Zips the entire contents of the given directory into an in-memory archive, preserving the
   * directory structure with forward-slash separators and deterministic entry ordering.
   *
   * @param root the directory whose contents should be archived
   * @return the bytes of the resulting zip archive
   * @throws IOException when walking the directory tree or writing an entry fails
   */
  private byte[] zipDirectory(Path root) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
        if (root.equals(path)) {
          continue;
        }

        String entryName = root.relativize(path).toString().replace('\\', '/');
        if (Files.isDirectory(path)) {
          zipOutputStream.putNextEntry(new ZipEntry(entryName + "/"));
          zipOutputStream.closeEntry();
          continue;
        }

        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        Files.copy(path, zipOutputStream);
        zipOutputStream.closeEntry();
      }
    }
    return outputStream.toByteArray();
  }

  /**
   * Recursively deletes the given directory and all of its contents on a best-effort basis,
   * silently ignoring any individual deletion failures.
   *
   * @param root the directory to delete; ignored when {@code null} or non-existent
   */
  private void deleteRecursively(Path root) {
    if (root == null || !Files.exists(root)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  log.error(e.getMessage());
                }
              });
    } catch (IOException e) {
      log.error(e.getMessage());
    }
  }

  /**
   * A paired metamodel (ecore) file and its corresponding genmodel file.
   *
   * @param metamodelFile the metamodel (ecore) file; must not be {@code null}
   * @param genmodelFile the genmodel file; must not be {@code null}
   */
  public record ModelFiles(File metamodelFile, File genmodelFile) {
    /**
     * Validates that both files are present.
     *
     * @throws NullPointerException when either file is {@code null}
     */
    public ModelFiles {
      Objects.requireNonNull(metamodelFile, "metamodelFile must not be null");
      Objects.requireNonNull(genmodelFile, "genmodelFile must not be null");
    }
  }
}
