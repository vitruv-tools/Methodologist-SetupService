package tools.vitruv.methodologist.setup.vsum.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Deletes build workspaces left over from earlier runs on startup: those of a crashed service, and
 * those kept for debugging (see {@code setup.vsum.keep-failed-workspaces}) once they are older than
 * {@code setup.vsum.workspace-retention}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VsumWorkspaceCleaner implements ApplicationRunner {

  /** Prefixes of the temporary directories created for builds and project archives. */
  static final List<String> WORKSPACE_PREFIXES =
      List.of(VsumProjectBuildService.WORKSPACE_PREFIX, "vsum-project-");

  private final VsumBuildProperties properties;

  @Override
  public void run(ApplicationArguments args) {
    deleteWorkspaces(
        Path.of(System.getProperty("java.io.tmpdir")),
        Instant.now().minus(properties.getWorkspaceRetention()));
  }

  /**
   * Deletes the workspaces in the directory that were last modified before the cutoff.
   *
   * @param directory the directory containing the workspaces
   * @param cutoff workspaces modified before this instant are deleted
   * @return the number of deleted workspaces
   */
  int deleteWorkspaces(Path directory, Instant cutoff) {
    List<Path> workspaces;
    try (Stream<Path> paths = Files.list(directory)) {
      workspaces = paths.filter(VsumWorkspaceCleaner::isWorkspace).toList();
    } catch (IOException e) {
      log.warn("Could not list {} to clean up build workspaces: {}", directory, e.getMessage());
      return 0;
    }
    int deleted = 0;
    for (Path workspace : workspaces) {
      try {
        if (Files.getLastModifiedTime(workspace).toInstant().isBefore(cutoff)) {
          deleteRecursively(workspace);
          deleted++;
        }
      } catch (IOException e) {
        log.warn("Could not delete build workspace {}: {}", workspace, e.getMessage());
      }
    }
    if (deleted > 0) {
      log.info("Deleted {} leftover build workspace(s) in {}", deleted, directory);
    }
    return deleted;
  }

  private static boolean isWorkspace(Path path) {
    String name = path.getFileName().toString();
    return Files.isDirectory(path) && WORKSPACE_PREFIXES.stream().anyMatch(name::startsWith);
  }

  private static void deleteRecursively(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
