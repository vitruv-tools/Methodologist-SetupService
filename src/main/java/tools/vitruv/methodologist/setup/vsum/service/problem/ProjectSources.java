package tools.vitruv.methodologist.setup.vsum.service.problem;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;

/**
 * Where the uploaded files ended up in a generated project, so that build errors, which refer to
 * the project, can be attributed to the uploads.
 */
public final class ProjectSources {

  private final Path projectRoot;
  private final Map<Path, SourceFile> filesByPath = new LinkedHashMap<>();
  private final Map<String, SourceFile> reactionsBySegment = new HashMap<>();
  private final Map<String, SourceFile> metamodelsByJavaPackage = new LinkedHashMap<>();
  private final Map<String, SourceFile> constraintsByResource = new HashMap<>();
  private final Map<String, SourceFile> viewsByExport = new HashMap<>();
  private final Map<String, SourceFile> viewsByName = new HashMap<>();

  /**
   * Creates an empty mapping.
   *
   * @param projectRoot root of the generated project
   */
  public ProjectSources(Path projectRoot) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
  }

  /**
   * An uploaded file as placed into the project.
   *
   * @param source the kind of input
   * @param fileName the name of the upload
   * @param text the content of the upload
   * @param uploadedLines for each 0-based line of the file as written into the project, the 1-based
   *     line of the upload; {@code null} if both are the same
   */
  public record SourceFile(Source source, String fileName, String text, int[] uploadedLines) {

    /**
     * Maps a line of the file in the project to the line of the upload.
     *
     * @param writtenLine 1-based line in the project
     * @return the 1-based line in the upload
     */
    public int uploadedLine(int writtenLine) {
      if (uploadedLines == null || writtenLine < 1 || writtenLine > uploadedLines.length) {
        return writtenLine;
      }
      return uploadedLines[writtenLine - 1];
    }
  }

  /**
   * Records an upload written into the project.
   *
   * @param source the kind of input
   * @param path the file in the project
   * @param upload the uploaded file
   * @param uploadedLines line mapping, see {@link SourceFile#uploadedLines()}
   * @return the recorded file
   */
  public SourceFile add(Source source, Path path, UploadedFile upload, int[] uploadedLines) {
    SourceFile file = new SourceFile(source, upload.fileName(), upload.text(), uploadedLines);
    filesByPath.put(path.toAbsolutePath().normalize(), file);
    return file;
  }

  /**
   * Records the reactions file that declares a segment.
   *
   * @param segment the segment name
   * @param file the reactions file
   */
  public void addSegment(String segment, SourceFile file) {
    reactionsBySegment.put(segment.toLowerCase(Locale.ROOT), file);
  }

  /**
   * Records the metamodel whose code is generated into a Java package.
   *
   * @param javaPackage the generated package, e.g. {@code
   *     tools.vitruv.methodologisttemplate.model.model2}
   * @param file the metamodel
   */
  public void addMetamodelPackage(String javaPackage, SourceFile file) {
    metamodelsByJavaPackage.put(javaPackage, file);
  }

  /**
   * Records the constraints file behind a classpath resource.
   *
   * @param resource the classpath resource
   * @param file the constraints file
   */
  public void addConstraintResource(String resource, SourceFile file) {
    constraintsByResource.put(resource, file);
  }

  /**
   * Records the view definition of a view type.
   *
   * @param exportName the exported package, into which NeoJoin generates the view type, e.g. {@code
   *     components} for the generated package {@code neojoin.viewtypes.components}
   * @param viewTypeName the name of the view type
   * @param file the view definition
   */
  public void addView(String exportName, String viewTypeName, SourceFile file) {
    viewsByExport.put(exportName, file);
    viewsByName.put(viewTypeName, file);
  }

  /**
   * Returns the root of the generated project.
   *
   * @return the absolute project root
   */
  public Path projectRoot() {
    return projectRoot;
  }

  /**
   * Returns the upload written to a path of the project.
   *
   * @param path a file in the project
   * @return the upload, if the file is one
   */
  public Optional<SourceFile> fileAt(Path path) {
    return Optional.ofNullable(filesByPath.get(path.toAbsolutePath().normalize()));
  }

  /**
   * Returns the reactions file declaring a segment.
   *
   * @param segment the segment name, in any case
   * @return the reactions file, if known
   */
  public Optional<SourceFile> reactionsOf(String segment) {
    return Optional.ofNullable(reactionsBySegment.get(segment.toLowerCase(Locale.ROOT)));
  }

  /**
   * Returns the metamodel generating a Java package or one of its parents.
   *
   * @param javaPackage a Java package
   * @return the metamodel, if known
   */
  public Optional<SourceFile> metamodelOf(String javaPackage) {
    String bestMatch = null;
    for (String known : metamodelsByJavaPackage.keySet()) {
      boolean matches = javaPackage.equals(known) || javaPackage.startsWith(known + ".");
      if (matches && (bestMatch == null || known.length() > bestMatch.length())) {
        bestMatch = known;
      }
    }
    return Optional.ofNullable(bestMatch).map(metamodelsByJavaPackage::get);
  }

  /**
   * Returns the constraints file behind a classpath resource.
   *
   * @param resource the classpath resource
   * @return the constraints file, if known
   */
  public Optional<SourceFile> constraintsOf(String resource) {
    return Optional.ofNullable(constraintsByResource.get(resource));
  }

  /**
   * Returns the view definition that exports a package.
   *
   * @param exportName the exported package
   * @return the view definition, if known
   */
  public Optional<SourceFile> viewOf(String exportName) {
    return Optional.ofNullable(viewsByExport.get(exportName));
  }

  /**
   * Returns the view definition of a view type.
   *
   * @param viewTypeName the name of the view type
   * @return the view definition, if known
   */
  public Optional<SourceFile> viewNamed(String viewTypeName) {
    return Optional.ofNullable(viewsByName.get(viewTypeName));
  }

  /**
   * Replaces paths into the project by upload names or project-relative paths, so that messages do
   * not expose the temporary workspace.
   *
   * @param text a message
   * @return the message without workspace paths
   */
  public String sanitize(String text) {
    String result = text;
    for (Map.Entry<Path, SourceFile> entry : filesByPath.entrySet()) {
      result = result.replace("file:" + entry.getKey(), entry.getValue().fileName());
      result = result.replace(entry.getKey().toString(), entry.getValue().fileName());
    }
    result = result.replace("file:" + projectRoot + "/", "");
    result = result.replace(projectRoot + "/", "");
    return result.replaceAll("(?:file:)?/\\S*?/vsum-build-\\d+/project/", "");
  }
}
