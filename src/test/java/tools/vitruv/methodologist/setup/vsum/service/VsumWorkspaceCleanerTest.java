package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VsumWorkspaceCleanerTest {

  private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

  @TempDir Path tmp;

  private final VsumWorkspaceCleaner cleaner = new VsumWorkspaceCleaner(new VsumBuildProperties());

  @Test
  void deletesBuildAndProjectWorkspacesOlderThanTheCutoff() throws IOException {
    Path oldBuild = Files.createDirectory(tmp.resolve("vsum-build-1"));
    Files.writeString(
        Files.createDirectories(oldBuild.resolve("project/vsum")).resolve("pom.xml"), "<p/>");
    Files.setLastModifiedTime(oldBuild, FileTime.from(NOW.minus(Duration.ofDays(2))));
    Path oldProject = workspace("vsum-project-2", NOW.minus(Duration.ofDays(2)));
    Path recentBuild = workspace("vsum-build-3", NOW.minus(Duration.ofMinutes(5)));

    int deleted = cleaner.deleteWorkspaces(tmp, NOW.minus(Duration.ofDays(1)));

    assertEquals(2, deleted);
    assertFalse(Files.exists(oldBuild));
    assertFalse(Files.exists(oldProject));
    assertTrue(Files.exists(recentBuild), "builds that may still run are kept");
  }

  @Test
  void leavesOtherDirectoriesAndFilesAlone() throws IOException {
    Path other = workspace("other-1", NOW.minus(Duration.ofDays(30)));
    Path file = Files.writeString(tmp.resolve("vsum-build-file"), "not a workspace");
    Files.setLastModifiedTime(file, FileTime.from(NOW.minus(Duration.ofDays(30))));

    assertEquals(0, cleaner.deleteWorkspaces(tmp, NOW));

    assertTrue(Files.exists(other));
    assertTrue(Files.exists(file));
  }

  @Test
  void toleratesAMissingDirectory() {
    assertEquals(0, cleaner.deleteWorkspaces(tmp.resolve("missing"), NOW));
  }

  private Path workspace(String name, Instant lastModified) throws IOException {
    Path workspace = Files.createDirectory(tmp.resolve(name));
    Files.setLastModifiedTime(workspace, FileTime.from(lastModified));
    return workspace;
  }
}
