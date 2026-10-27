package tools.vitruv.methodologist.setup.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.model.service.EcoreToGenmodelService;

/** Tests that uploaded constraints become part of the generated VSUM project. */
class VsumServiceConstraintsTest {

  private final GenerateFromTemplate generateFromTemplate = mock(GenerateFromTemplate.class);
  @TempDir Path tempDir;

  @Test
  void copiesConstraintsIntoTheVsumResourcesAndRegistersThem() throws Exception {
    File ecore = writeEcore();
    File genmodel = Files.writeString(tempDir.resolve("model.genmodel"), "x").toFile();
    File constraint =
        Files.writeString(tempDir.resolve("prices.ocl"), "context pcm::Item inv a: true").toFile();
    VsumService service = spy(new VsumService(generateFromTemplate, new EcoreToGenmodelService()));
    doNothing().when(service).runMavenBuild(any(Path.class), anyMap());

    byte[] archive =
        service.generateProjectArchive(
            List.of(new VsumService.ModelFiles(ecore, genmodel)),
            List.of(),
            List.of(constraint),
            Map.of());

    assertThat(readZipEntry(archive, "vsum/src/main/resources/constraints/prices.ocl"))
        .isEqualTo("context pcm::Item inv a: true");
    verify(generateFromTemplate).generateVsumPom(any(File.class), anyString(), eq(true));
    verify(generateFromTemplate)
        .generateVsumExample(
            any(File.class),
            anyString(),
            anyList(),
            anyList(),
            eq(List.of("constraints/prices.ocl")));
  }

  @Test
  void generatesProjectWithoutConstraints() throws Exception {
    File ecore = writeEcore();
    File genmodel = Files.writeString(tempDir.resolve("model.genmodel"), "x").toFile();
    VsumService service = spy(new VsumService(generateFromTemplate, new EcoreToGenmodelService()));
    doNothing().when(service).runMavenBuild(any(Path.class), anyMap());

    service.generateProjectArchive(
        List.of(new VsumService.ModelFiles(ecore, genmodel)), List.of(), Map.of());

    verify(generateFromTemplate).generateVsumPom(any(File.class), anyString(), eq(false));
    verify(generateFromTemplate)
        .generateVsumExample(any(File.class), anyString(), anyList(), anyList(), eq(List.of()));
  }

  @Test
  void rejectsNsUrisThatCouldInjectCommands() throws Exception {
    VsumService service = new VsumService(generateFromTemplate, new EcoreToGenmodelService());
    var method =
        VsumService.class.getDeclaredMethod("createMavenProcessBuilder", Path.class, Map.class);
    method.setAccessible(true);

    assertThatThrownBy(() -> method.invoke(service, tempDir, Map.of("pcm", "http://x&calc")))
        .hasCauseInstanceOf(MethodologistSetupException.class);
    assertThatThrownBy(() -> method.invoke(service, tempDir, Map.of("a|b", "http://pcm")))
        .hasCauseInstanceOf(MethodologistSetupException.class);
  }

  private File writeEcore() throws IOException {
    return Files.writeString(
            tempDir.resolve("model.ecore"),
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ecore:EPackage xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
                name="pcm" nsURI="http://pcm" nsPrefix="pcm"/>
            """)
        .toFile();
  }

  private static String readZipEntry(byte[] archive, String name) throws IOException {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (entry.getName().equals(name)) {
          return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
    }
    return null;
  }
}
