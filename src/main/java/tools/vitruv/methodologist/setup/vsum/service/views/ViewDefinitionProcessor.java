package tools.vitruv.methodologist.setup.vsum.service.views;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;

/** Integrates view definitions into the generated VSUM project. */
public interface ViewDefinitionProcessor {

  /**
   * Checks the view definitions and adds them to the generated project.
   *
   * @param viewDefinitions uploaded view definition files
   * @param metamodels the processed metamodels the views are defined on
   * @param projectRoot root of the generated project
   * @return the view types generated from the view definitions, in upload order
   * @throws VsumBuildException when a view definition is invalid
   * @throws IOException when writing to the project fails
   */
  List<GeneratedViewType> process(
      List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels, Path projectRoot)
      throws IOException;

  /**
   * Runs the same checks as {@link #process(List, List, Path)} without writing anything.
   *
   * @param viewDefinitions uploaded view definition files
   * @param metamodels the checked metamodels the views are defined on
   * @return the view types generated from the view definitions, in upload order
   * @throws VsumBuildException when a view definition is invalid
   */
  List<GeneratedViewType> check(
      List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels);
}
