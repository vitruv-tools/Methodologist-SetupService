package tools.vitruv.methodologist.setup.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VsumServiceTest {

  private final GenerateFromTemplate generateFromTemplate = mock(GenerateFromTemplate.class);
  @TempDir Path tempDir;

  @Test
  void generateProjectArchive_shouldThrowWhenModelFilesNull() {
    VsumService service = spy(new VsumService(generateFromTemplate));

    assertThatThrownBy(() -> service.generateProjectArchive(null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one metamodel/genmodel pair");
  }

  @Test
  void generateProjectArchive_shouldThrowWhenModelFilesEmpty() {
    VsumService service = spy(new VsumService(generateFromTemplate));

    assertThatThrownBy(() -> service.generateProjectArchive(List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void generateProjectArchive_shouldThrowWhenMetamodelMissing() throws IOException {
    File missing = tempDir.resolve("missing.ecore").toFile();

    File genmodel = Files.createFile(tempDir.resolve("model.genmodel")).toFile();

    VsumService.ModelFiles modelFiles = new VsumService.ModelFiles(missing, genmodel);

    VsumService service = spy(new VsumService(generateFromTemplate));

    assertThatThrownBy(() -> service.generateProjectArchive(List.of(modelFiles), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("metamodelFile does not exist");
  }

  @Test
  void generateProjectArchive_shouldThrowWhenReactionFileMissing() throws IOException {
    File ecore = Files.createFile(tempDir.resolve("model.ecore")).toFile();
    File genmodel = Files.createFile(tempDir.resolve("model.genmodel")).toFile();

    File missingReaction = tempDir.resolve("missing.reactions").toFile();

    VsumService.ModelFiles modelFiles = new VsumService.ModelFiles(ecore, genmodel);

    VsumService service = spy(new VsumService(generateFromTemplate));

    assertThatThrownBy(
            () -> service.generateProjectArchive(List.of(modelFiles), List.of(missingReaction)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reactionFile does not exist");
  }

  @Test
  void generateProjectArchive_shouldGenerateArchive() throws Exception {

    File ecore = Files.createFile(tempDir.resolve("model.ecore")).toFile();
    Files.writeString(
        ecore.toPath(),
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <ecore:EPackage
                xmlns:xmi="http://www.omg.org/XMI"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
                name="test"
                nsURI="http://test"
                nsPrefix="test">
            </ecore:EPackage>
            """);
    File genmodel = Files.createFile(tempDir.resolve("model.genmodel")).toFile();
    Files.writeString(
        genmodel.toPath(),
        """
              <?xml version="1.0" encoding="UTF-8"?>
              <genmodel:GenModel
                  xmlns:xmi="http://www.omg.org/XMI"
                  xmlns:genmodel="http://www.eclipse.org/emf/2002/GenModel"
                  modelDirectory="/test/src"
                  modelPluginID="test"
                  importerID="org.eclipse.emf.importer.ecore">
              </genmodel:GenModel>
              """);
    File reaction = Files.createFile(tempDir.resolve("sample.reactions")).toFile();

    VsumService.ModelFiles modelFiles = new VsumService.ModelFiles(ecore, genmodel);

    VsumService service = spy(new VsumService(generateFromTemplate));

    doAnswer(
            invocation -> {
              Path workspace = ((File) invocation.getArgument(0)).toPath().getParent().getParent();

              Files.createDirectories(workspace);
              return null;
            })
        .when(generateFromTemplate)
        .generateRootPom(any(File.class), anyString());

    doNothing().when(service).runMavenBuild(any(Path.class), anyMap());

    byte[] archive =
        service.generateProjectArchive(
            List.of(modelFiles), List.of(reaction), Map.of("pcm", "http://pcm/ns"));

    assertThat(archive).isNotNull();
    assertThat(archive.length).isGreaterThan(0);

    verify(service).runMavenBuild(any(Path.class), eq(Map.of("pcm", "http://pcm/ns")));
  }

  @Test
  void generateProjectArchive_shouldDelegateToThreeArgumentMethod() throws Exception {

    File ecore = mock(File.class);
    File genmodel = mock(File.class);

    VsumService.ModelFiles modelFiles = new VsumService.ModelFiles(ecore, genmodel);

    VsumService service = spy(new VsumService(generateFromTemplate));

    byte[] expected = {1, 2, 3};

    doReturn(expected)
        .when(service)
        .generateProjectArchive(eq(List.of(modelFiles)), eq(List.of()), eq(Map.of()));

    byte[] result = service.generateProjectArchive(List.of(modelFiles), List.of());

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void modelFiles_shouldRejectNullMetamodel() {
    File genmodel = new File("test.genmodel");

    assertThatThrownBy(() -> new VsumService.ModelFiles(null, genmodel))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("metamodelFile");
  }

  @Test
  void modelFiles_shouldRejectNullGenmodel() {
    File ecore = new File("test.ecore");

    assertThatThrownBy(() -> new VsumService.ModelFiles(ecore, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("genmodelFile");
  }

  @Test
  void runMavenBuild_shouldFailWhenExitCodeNonZero() throws Exception {

    VsumService service = new VsumService(generateFromTemplate);

    Path projectRoot = tempDir;

    assertThatThrownBy(() -> service.runMavenBuild(projectRoot, Map.of("invalid", "value")))
        .isInstanceOf(IOException.class);
  }

  @Test
  void createMavenProcessBuilderNamespaceProperties_shouldBePassed() throws Exception {

    VsumService service = new VsumService(generateFromTemplate);

    var method =
        VsumService.class.getDeclaredMethod("createMavenProcessBuilder", Path.class, Map.class);

    method.setAccessible(true);

    ProcessBuilder builder =
        (ProcessBuilder)
            method.invoke(
                service,
                tempDir,
                Map.of(
                    "pcm", "http://pcm",
                    "uml", "http://uml"));

    assertThat(builder.command())
        .contains("-Dmetamodel.pcm.nsuri=http://pcm")
        .contains("-Dmetamodel.uml.nsuri=http://uml");

    assertThat(builder.directory()).isEqualTo(tempDir.toFile());
  }
}
