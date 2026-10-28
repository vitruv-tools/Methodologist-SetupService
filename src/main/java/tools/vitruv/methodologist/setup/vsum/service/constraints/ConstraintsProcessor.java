package tools.vitruv.methodologist.setup.vsum.service.constraints;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Places uploaded VitruvOCL constraint files into the generated project. They end up on the VSUM
 * classpath, where the generated VSUM evaluates them after every change propagation and the
 * generated smoke test checks that they compile.
 */
@Service
public class ConstraintsProcessor {

  /**
   * Copies the constraint files into the consistency module's constraint resource directory.
   *
   * @param constraints uploaded {@code .ocl} files
   * @param projectRoot root of the generated project
   * @return classpath resource names of the constraint files
   * @throws IOException when writing the files fails
   */
  public List<String> process(List<UploadedFile> constraints, Path projectRoot) throws IOException {
    if (constraints.isEmpty()) {
      return List.of();
    }
    Path directory = ProjectLayout.constraintsDirectory(projectRoot);
    Files.createDirectories(directory);

    List<String> resources = new ArrayList<>();
    for (UploadedFile file : constraints) {
      Files.write(directory.resolve(file.fileName()), file.content());
      resources.add(ProjectLayout.CONSISTENCY_RESOURCE_PACKAGE + "/" + file.fileName());
    }
    return resources;
  }
}
