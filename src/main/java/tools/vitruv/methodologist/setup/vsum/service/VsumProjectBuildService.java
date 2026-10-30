package tools.vitruv.methodologist.setup.vsum.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.messages.ErrorMessages;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildFailedException;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildRunner;
import tools.vitruv.methodologist.setup.vsum.service.constraints.ConstraintsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.MetamodelProcessor;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildOutputParser;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.ProjectSources;
import tools.vitruv.methodologist.setup.vsum.service.problem.ProjectSources.SourceFile;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectGenerator;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;
import tools.vitruv.methodologist.setup.vsum.service.reactions.ReactionsProcessor;
import tools.vitruv.methodologist.setup.vsum.service.views.ViewDefinitionProcessor;

/**
 * Builds a VSUM from a methodologist's metamodels, reactions, constraints, and view definitions.
 *
 * <p>The pipeline validates all inputs before anything is built, generates the genmodels from the
 * metamodels, generates a project following the Methodologist Template, and builds it with Maven,
 * including a smoke test that instantiates the VSUM. The temporary workspace is always deleted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VsumProjectBuildService {

  /** Prefix of the temporary build workspaces. */
  static final String WORKSPACE_PREFIX = "vsum-build-";

  private final MetamodelProcessor metamodelProcessor;
  private final ReactionsProcessor reactionsProcessor;
  private final ConstraintsProcessor constraintsProcessor;
  private final ViewDefinitionProcessor viewDefinitionProcessor;
  private final ProjectGenerator projectGenerator;
  private final MavenBuildRunner mavenBuildRunner;
  private final VsumBuildProperties properties;

  /**
   * Builds the VSUM and returns the generated project as a zip archive (without build output).
   *
   * @param request the build inputs
   * @return the zip archive bytes
   */
  public byte[] buildProjectArchive(VsumBuildRequest request) {
    return build(request, VsumProjectBuildService::zipProject);
  }

  /**
   * Builds the VSUM and returns its executable jar-with-dependencies.
   *
   * @param request the build inputs
   * @return the jar bytes
   */
  public byte[] buildProjectJar(VsumBuildRequest request) {
    return build(request, VsumProjectBuildService::readVsumJar);
  }

  private byte[] build(VsumBuildRequest request, ArtifactExtractor extractor) {
    Path workspace = null;
    boolean keepWorkspace = false;
    try {
      workspace = Files.createTempDirectory(WORKSPACE_PREFIX);
      Path projectRoot = workspace.resolve("project");
      ProjectSources sources = generateProject(request, projectRoot);
      try {
        mavenBuildRunner.build(projectRoot, workspace);
      } catch (MavenBuildFailedException e) {
        throw new VsumBuildException(
            e.getErrorCode(), e.getHeader(), BuildOutputParser.parse(e, sources), e.getStatus());
      }
      return extractor.extract(projectRoot);
    } catch (MethodologistSetupException e) {
      keepWorkspace = properties.isKeepFailedWorkspaces() || isNotCausedByTheUploads(e);
      throw e;
    } catch (IOException e) {
      keepWorkspace = properties.isKeepFailedWorkspaces();
      throw new MethodologistSetupException(
          VsumErrorCodes.BUILD_ERROR,
          "Failed to build the VSUM project: " + e.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR,
          e);
    } finally {
      if (keepWorkspace && workspace != null) {
        log.warn("Keeping workspace of failed VSUM build: {}", workspace);
      } else {
        deleteRecursively(workspace);
      }
    }
  }

  /**
   * Failures of the build environment or the service itself are kept for debugging; they are
   * deleted on a later startup (see {@link VsumWorkspaceCleaner}).
   */
  private static boolean isNotCausedByTheUploads(MethodologistSetupException e) {
    return VsumErrorCodes.INTERNAL_ERROR.equals(e.getErrorCode())
        || VsumErrorCodes.BUILD_UNAVAILABLE.equals(e.getErrorCode());
  }

  /**
   * Validates the inputs and writes the complete project, without building it.
   *
   * @param request the build inputs
   * @param projectRoot directory to generate the project into
   * @return where the uploads were placed, to attribute build errors to them
   * @throws IOException when writing the project fails
   */
  ProjectSources generateProject(VsumBuildRequest request, Path projectRoot) throws IOException {
    List<GeneratedMetamodel> metamodels =
        metamodelProcessor.process(request.metamodels(), projectRoot);
    List<String> specifications =
        reactionsProcessor.process(request.reactions(), metamodels, projectRoot);
    List<String> constraintResources =
        constraintsProcessor.process(request.constraints(), projectRoot);
    viewDefinitionProcessor.process(request.viewDefinitions(), metamodels, projectRoot);
    projectGenerator.generate(projectRoot, metamodels, specifications, constraintResources);
    return sources(request, metamodels, constraintResources, projectRoot);
  }

  private static ProjectSources sources(
      VsumBuildRequest request,
      List<GeneratedMetamodel> metamodels,
      List<String> constraintResources,
      Path projectRoot) {
    ProjectSources sources = new ProjectSources(projectRoot);
    // the metamodel processor returns one entry per upload, in upload order
    for (int i = 0; i < metamodels.size() && i < request.metamodels().size(); i++) {
      GeneratedMetamodel metamodel = metamodels.get(i);
      SourceFile file =
          sources.add(
              Source.METAMODEL,
              ProjectLayout.ecoreDirectory(projectRoot).resolve(metamodel.ecoreFileName()),
              request.metamodels().get(i),
              null);
      for (GeneratedMetamodel.GeneratedPackage generated : metamodel.packages()) {
        String packageInterface = generated.packageInterface();
        sources.addMetamodelPackage(
            packageInterface.substring(0, packageInterface.lastIndexOf('.')), file);
      }
    }
    for (UploadedFile reactions : request.reactions()) {
      String text = reactions.text();
      SourceFile file =
          sources.add(
              Source.REACTIONS,
              ProjectLayout.reactionsDirectory(projectRoot).resolve(reactions.fileName()),
              reactions,
              ReactionsProcessor.uploadedLines(text));
      ReactionsProcessor.segments(ReactionsProcessor.stripComments(text))
          .forEach(segment -> sources.addSegment(segment.name(), file));
    }
    for (int i = 0; i < constraintResources.size() && i < request.constraints().size(); i++) {
      UploadedFile constraints = request.constraints().get(i);
      SourceFile file =
          sources.add(
              Source.CONSTRAINTS,
              ProjectLayout.constraintsDirectory(projectRoot).resolve(constraints.fileName()),
              constraints,
              null);
      sources.addConstraintResource(constraintResources.get(i), file);
    }
    for (UploadedFile view : request.viewDefinitions()) {
      sources.add(
          Source.VIEWS,
          ProjectLayout.viewsDirectory(projectRoot).resolve(view.fileName()),
          view,
          null);
    }
    return sources;
  }

  private static byte[] readVsumJar(Path projectRoot) throws IOException {
    Path jar = ProjectLayout.vsumJar(projectRoot);
    if (!Files.isRegularFile(jar)) {
      throw new MethodologistSetupException(
          VsumErrorCodes.BUILD_ERROR,
          String.format(ErrorMessages.VSUM_JAR_NOT_FOUND, projectRoot.relativize(jar)),
          HttpStatus.INTERNAL_SERVER_ERROR,
          null);
    }
    return Files.readAllBytes(jar);
  }

  /** Zips the project sources, leaving out the {@code target} build output directories. */
  private static byte[] zipProject(Path projectRoot) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes);
        Stream<Path> paths = Files.walk(projectRoot)) {
      for (Path path : paths.sorted().toList()) {
        Path relative = projectRoot.relativize(path);
        if (relative.toString().isEmpty() || isBuildOutput(relative)) {
          continue;
        }
        String entryName = relative.toString().replace('\\', '/');
        if (Files.isDirectory(path)) {
          zip.putNextEntry(new ZipEntry(entryName + "/"));
        } else {
          zip.putNextEntry(new ZipEntry(entryName));
          Files.copy(path, zip);
        }
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  private static boolean isBuildOutput(Path relative) {
    for (Path segment : relative) {
      if (segment.toString().equals("target")) {
        return true;
      }
    }
    return false;
  }

  private static void deleteRecursively(Path root) {
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
                  log.warn("Could not delete {}: {}", path, e.getMessage());
                }
              });
    } catch (IOException e) {
      log.warn("Could not clean up workspace {}: {}", root, e.getMessage());
    }
  }

  /** Extracts the response bytes from a successfully built project. */
  @FunctionalInterface
  private interface ArtifactExtractor {
    byte[] extract(Path projectRoot) throws IOException;
  }
}
