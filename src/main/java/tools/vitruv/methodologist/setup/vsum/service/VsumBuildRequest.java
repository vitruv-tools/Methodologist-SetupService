package tools.vitruv.methodologist.setup.vsum.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

/**
 * Everything a methodologist supplies to build a VSUM.
 *
 * @param metamodels Ecore metamodels; at least one is required
 * @param reactions Reactions consistency specifications, may be empty
 * @param constraints VitruvOCL constraint files, may be empty
 * @param viewDefinitions NeoJoin view definitions, may be empty
 */
public record VsumBuildRequest(
    List<UploadedFile> metamodels,
    List<UploadedFile> reactions,
    List<UploadedFile> constraints,
    List<UploadedFile> viewDefinitions) {

  /** File extension of Ecore metamodels. */
  public static final String METAMODEL_EXTENSION = ".ecore";

  /** File extension of Reactions files. */
  public static final String REACTIONS_EXTENSION = ".reactions";

  /** File extension of VitruvOCL constraint files. */
  public static final String CONSTRAINTS_EXTENSION = ".ocl";

  /** Makes the lists immutable and rejects {@code null} lists. */
  public VsumBuildRequest {
    metamodels = List.copyOf(metamodels);
    reactions = List.copyOf(reactions);
    constraints = List.copyOf(constraints);
    viewDefinitions = List.copyOf(viewDefinitions);
  }

  /**
   * Reads and validates multipart uploads into a build request.
   *
   * @param metamodelFiles Ecore metamodel uploads; at least one is required
   * @param reactionFiles Reactions uploads, may be {@code null}
   * @param constraintFiles VitruvOCL uploads, may be {@code null}
   * @param viewFiles NeoJoin view definition uploads, may be {@code null}
   * @return the validated request
   * @throws MethodologistSetupException when an upload is missing, empty, misnamed, or duplicated
   */
  public static VsumBuildRequest fromMultipart(
      List<MultipartFile> metamodelFiles,
      List<MultipartFile> reactionFiles,
      List<MultipartFile> constraintFiles,
      List<MultipartFile> viewFiles) {
    if (metamodelFiles == null || metamodelFiles.isEmpty()) {
      throw new MethodologistSetupException(
          VsumErrorCodes.INPUT_ERROR, "At least one metamodel (.ecore) file is required");
    }
    return new VsumBuildRequest(
        read(metamodelFiles, "metamodel", METAMODEL_EXTENSION),
        read(reactionFiles, "reactions", REACTIONS_EXTENSION),
        read(constraintFiles, "constraint", CONSTRAINTS_EXTENSION),
        read(viewFiles, "view definition", null));
  }

  private static List<UploadedFile> read(
      List<MultipartFile> files, String label, String extension) {
    if (files == null) {
      return List.of();
    }
    List<UploadedFile> uploaded =
        files.stream().map(file -> UploadedFile.fromMultipart(file, label, extension)).toList();
    Set<String> names = new HashSet<>();
    for (UploadedFile file : uploaded) {
      if (!names.add(file.fileName().toLowerCase())) {
        throw new MethodologistSetupException(
            VsumErrorCodes.INPUT_ERROR,
            String.format(
                "The %s file name '%s' was uploaded more than once", label, file.fileName()));
      }
    }
    return uploaded;
  }
}
