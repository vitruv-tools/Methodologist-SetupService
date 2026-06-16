package tools.vitruv.methodologist.setup.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.exception.MissingModelException;

class VsumProjectBuildServiceTest {

  private final VsumService vsumService = mock(VsumService.class);
  private final VsumProjectBuildService service = new VsumProjectBuildService(vsumService);
  @TempDir java.nio.file.Path tempDir;

  @Test
  void buildProjectArchive_shouldThrowWhenMetamodelFilesNull() {

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(
                    null, List.of(mockMultipart("model.genmodel")), List.of()))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("At least one metamodel");
  }

  @Test
  void buildProjectArchive_shouldThrowWhenGenmodelFilesNull() {

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(List.of(mockMultipart("model.ecore")), null, List.of()))
        .isInstanceOf(MethodologistSetupException.class);
  }

  @Test
  void buildProjectArchive_shouldThrowWhenCountsDoNotMatch() {

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(
                    List.of(mockMultipart("a.ecore"), mockMultipart("b.ecore")),
                    List.of(mockMultipart("a.genmodel")),
                    List.of()))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("counts must be identical");
  }

  @Test
  void buildProjectArchive_shouldThrowWhenMetamodelEmpty() {

    MultipartFile empty =
        new MockMultipartFile("file", "model.ecore", "application/xml", new byte[0]);

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(
                    List.of(empty), List.of(mockMultipart("model.genmodel")), List.of()))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("metamodel file");
  }

  @Test
  void buildProjectArchive_shouldThrowWhenGenmodelEmpty() {

    MultipartFile empty =
        new MockMultipartFile("file", "model.genmodel", "application/xml", new byte[0]);

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(
                    List.of(mockMultipart("model.ecore")), List.of(empty), List.of()))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("genmodel file");
  }

  @Test
  void buildProjectArchive_shouldBuildArchive() throws Exception {

    byte[] expected = {1, 2, 3};

    when(vsumService.generateProjectArchive(anyList(), anyList(), anyMap())).thenReturn(expected);

    byte[] archive =
        service.buildProjectArchive(
            List.of(validMetamodel()),
            List.of(mockMultipart("model.genmodel")),
            List.of(reactionFile()));

    assertThat(archive).isEqualTo(expected);

    verify(vsumService).generateProjectArchive(anyList(), anyList(), anyMap());
  }

  @Test
  void buildProjectArchive_shouldBuildArchiveWithoutReactionFiles() throws Exception {

    when(vsumService.generateProjectArchive(anyList(), anyList(), anyMap()))
        .thenReturn(new byte[] {1});

    byte[] archive =
        service.buildProjectArchive(
            List.of(validMetamodel()), List.of(mockMultipart("model.genmodel")), List.of());

    assertThat(archive).isNotEmpty();
  }

  @Test
  void buildProjectArchive_shouldWrapIOException() throws Exception {

    when(vsumService.generateProjectArchive(anyList(), anyList(), anyMap()))
        .thenThrow(new java.io.IOException("boom"));

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(
                    List.of(validMetamodel()), List.of(mockMultipart("model.genmodel")), List.of()))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("Failed to build VSUM project archive");
  }

  @Test
  void buildProjectArchive_shouldWrapMissingModelException() throws Exception {

    when(vsumService.generateProjectArchive(anyList(), anyList(), anyMap()))
        .thenThrow(new MissingModelException("missing"));

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(
                    List.of(validMetamodel()), List.of(mockMultipart("model.genmodel")), List.of()))
        .isInstanceOf(MethodologistSetupException.class);
  }

  @Test
  void buildProjectArchive_shouldWrapInterruptedException() throws Exception {

    when(vsumService.generateProjectArchive(anyList(), anyList(), anyMap()))
        .thenThrow(new InterruptedException());

    assertThatThrownBy(
            () ->
                service.buildProjectArchive(
                    List.of(validMetamodel()), List.of(mockMultipart("model.genmodel")), List.of()))
        .isInstanceOf(MethodologistSetupException.class);

    assertThat(Thread.currentThread().isInterrupted()).isTrue();

    Thread.interrupted();
  }

  @Test
  void extractMetamodelNamespaceMap_shouldExtractNamespaceInformation() {

    File ecore =
        writeTempEcore(
            """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <ecore:EPackage
                            xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
                            name="pcm"
                            nsURI="http://pcm"/>
                        """);

    VsumService.ModelFiles pair =
        new VsumService.ModelFiles(ecore, tempDir.resolve("test.genmodel").toFile());

    Map<String, String> result = service.extractMetamodelNamespaceMap(List.of(pair));

    assertThat(result).containsEntry("pcm", "http://pcm");
  }

  @Test
  void extractMetamodelNamespaceMap_shouldIgnoreInvalidMetamodel() {

    File invalid = writeTempFile("broken.ecore", "not xml");

    VsumService.ModelFiles pair =
        new VsumService.ModelFiles(invalid, tempDir.resolve("test.genmodel").toFile());

    Map<String, String> result = service.extractMetamodelNamespaceMap(List.of(pair));

    assertThat(result).isEmpty();
  }

  private MockMultipartFile mockMultipart(String filename) {

    return new MockMultipartFile(
        "file", filename, "application/octet-stream", "content".getBytes(StandardCharsets.UTF_8));
  }

  private MockMultipartFile reactionFile() {

    return new MockMultipartFile(
        "file",
        "sample.reactions",
        "text/plain",
        """
                import "pcm"
                """
            .getBytes(StandardCharsets.UTF_8));
  }

  private MockMultipartFile validMetamodel() {

    return new MockMultipartFile(
        "file",
        "model.ecore",
        "application/xml",
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <ecore:EPackage
                    xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
                    name="pcm"
                    nsURI="http://pcm"/>
                """
            .getBytes(StandardCharsets.UTF_8));
  }

  private File writeTempEcore(String content) {

    return writeTempFile("model.ecore", content);
  }

  private File writeTempFile(String name, String content) {

    try {
      File file = tempDir.resolve(name).toFile();
      java.nio.file.Files.writeString(file.toPath(), content);
      return file;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
