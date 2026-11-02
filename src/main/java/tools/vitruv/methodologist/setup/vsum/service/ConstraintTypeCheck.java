package tools.vitruv.methodologist.setup.vsum.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import tools.vitruv.dsls.vitruvocl.common.CompileError;
import tools.vitruv.dsls.vitruvocl.pipeline.ConstraintResult;
import tools.vitruv.dsls.vitruvocl.pipeline.MetamodelWrapper;
import tools.vitruv.dsls.vitruvocl.pipeline.VitruvOCL;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

/**
 * Compiles uploaded VitruviusOCL constraint files against the uploaded metamodels, so that a VSUM
 * with invalid constraints is not built: the generated VSUM would load the constraints only when it
 * starts, and fail then.
 *
 * <p>The metamodels are loaded into a wrapper of each check, which resolves the types of the
 * constraints, so concurrent builds type-check against their own metamodels. The wrapper also
 * registers the packages by nsURI in the global {@link org.eclipse.emf.ecore.EPackage.Registry}:
 * there, builds with the same nsURI replace each other's packages, and the packages stay registered
 * after the check.
 */
final class ConstraintTypeCheck {
  private static final String VSUM_INPUT_ERROR_CODE = "VSUM_INPUT_ERROR";

  /** At most this many errors are reported per file, the first ones are the most helpful. */
  private static final int MAX_REPORTED_ERRORS = 5;

  private ConstraintTypeCheck() {}

  /**
   * Compiles each constraint file against the given metamodels.
   *
   * @param constraintFiles the constraint files
   * @param metamodelFiles the {@code .ecore} files of the VSUM
   * @throws IOException when reading a file fails
   * @throws MethodologistSetupException when a file has syntax or type errors, naming the file and
   *     the line (from 1) and column (from 0) of each error
   */
  static void check(List<File> constraintFiles, List<File> metamodelFiles) throws IOException {
    if (constraintFiles.isEmpty()) {
      return;
    }
    MetamodelWrapper metamodels = new MetamodelWrapper();
    for (File metamodelFile : metamodelFiles) {
      metamodels.loadMetamodel(metamodelFile.toPath());
    }
    for (File constraintFile : constraintFiles) {
      String content = withoutImports(Files.readString(constraintFile.toPath()));
      if (content.isBlank()) {
        continue;
      }
      ConstraintResult result = VitruvOCL.evaluateConstraint(content, metamodels);
      List<CompileError> errors = result.getCompilerErrors();
      if (!errors.isEmpty()) {
        throw new MethodologistSetupException(
            VSUM_INPUT_ERROR_CODE,
            String.format(
                "Constraint file %s has errors: %s",
                constraintFile.getName(),
                errors.stream()
                    .limit(MAX_REPORTED_ERRORS)
                    .map(
                        error ->
                            String.format(
                                "line %d, column %d: %s",
                                error.getLine(), error.getColumn(), error.getMessage()))
                    .collect(Collectors.joining("; "))));
      }
    }
  }

  /** Removes import declarations, keeping the line count so that positions stay valid. */
  private static String withoutImports(String content) {
    StringBuilder result = new StringBuilder();
    for (String line : content.split("\\R", -1)) {
      result.append(line.strip().startsWith("import ") ? "" : line).append('\n');
    }
    return result.toString();
  }
}
