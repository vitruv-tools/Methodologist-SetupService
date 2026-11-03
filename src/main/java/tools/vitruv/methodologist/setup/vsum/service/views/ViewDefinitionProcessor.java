package tools.vitruv.methodologist.setup.vsum.service.views;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;

/** Integrates view definitions into the generated VSUM project. */
public interface ViewDefinitionProcessor {

  /**
   * Adds the view definitions to the generated project.
   *
   * @param viewDefinitions uploaded view definition files
   * @param metamodels the processed metamodels the views are defined on
   * @param projectRoot root of the generated project
   * @throws IOException when writing to the project fails
   */
  void process(
      List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels, Path projectRoot)
      throws IOException;
}
