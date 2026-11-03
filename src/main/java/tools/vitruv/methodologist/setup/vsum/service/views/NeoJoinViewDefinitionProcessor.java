package tools.vitruv.methodologist.setup.vsum.service.views;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Stub for NeoJoin view definitions.
 *
 * <p>NeoJoin views are not compiled into the VSUM yet. The definitions are stored in the generated
 * project under {@code views/src/main/neojoin} so they are part of the returned project, but they
 * do not affect the build. Replace this implementation once NeoJoin integration is available.
 */
@Slf4j
@Service
public class NeoJoinViewDefinitionProcessor implements ViewDefinitionProcessor {

  @Override
  public void process(
      List<UploadedFile> viewDefinitions, List<GeneratedMetamodel> metamodels, Path projectRoot)
      throws IOException {
    if (viewDefinitions.isEmpty()) {
      return;
    }
    Path directory = ProjectLayout.viewsDirectory(projectRoot);
    Files.createDirectories(directory);
    for (UploadedFile file : viewDefinitions) {
      Files.write(directory.resolve(file.fileName()), file.content());
    }
    log.warn(
        "NeoJoin view definitions are not supported yet; {} file(s) were stored but not compiled",
        viewDefinitions.size());
  }
}
