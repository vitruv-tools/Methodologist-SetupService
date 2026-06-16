package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateFromTemplateTest {

  @TempDir Path tempDir;

  @Test
  void generateRootPomCreatesFileFromTemplate() throws Exception {
    GenerateFromTemplate generator = new GenerateFromTemplate();
    Path target = tempDir.resolve("pom.xml");

    generator.generateRootPom(target.toFile(), "tools.vitruv.generated");

    assertTrue(Files.exists(target));
    String content = Files.readString(target);
    assertTrue(content.contains("tools.vitruv.generated"));
  }
}

