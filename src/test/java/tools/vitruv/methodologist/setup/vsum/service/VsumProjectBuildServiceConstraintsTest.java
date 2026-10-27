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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

/** Tests the handling of uploaded OCL constraint files. */
class VsumProjectBuildServiceConstraintsTest {

  private final VsumService vsumService = mock(VsumService.class);
  private final VsumProjectBuildService service = new VsumProjectBuildService(vsumService);

  @Test
  @SuppressWarnings("unchecked")
  void passesUploadedConstraintsToTheBuild() throws Exception {
    List<String> contents = new ArrayList<>();
    when(vsumService.generateProjectJar(anyList(), anyList(), anyList(), anyMap()))
        .thenAnswer(
            invocation -> {
              for (File file : (List<File>) invocation.getArgument(2)) {
                contents.add(Files.readString(file.toPath()));
              }
              return new byte[] {1};
            });

    byte[] jar =
        service.buildProjectJar(
            List.of(metamodel()),
            List.of(genmodel()),
            List.of(reaction()),
            List.of(constraint("prices.ocl", "context pcm::Item inv positive: self.price > 0")));

    assertThat(jar).containsExactly(1);
    assertThat(contents).containsExactly("context pcm::Item inv positive: self.price > 0");
  }

  @Test
  void buildsWithoutConstraintsAsBefore() throws Exception {
    when(vsumService.generateProjectArchive(anyList(), anyList(), anyMap()))
        .thenReturn(new byte[] {2});

    service.buildProjectArchive(
        List.of(metamodel()), List.of(genmodel()), List.of(reaction()), null);

    verify(vsumService).generateProjectArchive(anyList(), anyList(), anyMap());
  }

  @Test
  @SuppressWarnings("unchecked")
  void keepsConstraintFilesWithEqualNamesApart() throws Exception {
    ArgumentCaptor<List<File>> captor = ArgumentCaptor.forClass(List.class);
    when(vsumService.generateProjectJar(anyList(), anyList(), captor.capture(), anyMap()))
        .thenReturn(new byte[] {1});

    service.buildProjectJar(
        List.of(metamodel()),
        List.of(genmodel()),
        List.of(reaction()),
        List.of(
            constraint("rules.ocl", "context pcm::Item inv a: true"),
            constraint("rules.ocl", "context pcm::Item inv b: true")));

    List<File> files = captor.getValue();
    assertThat(files).hasSize(2);
    assertThat(files.get(0).getName()).isNotEqualTo(files.get(1).getName());
    assertThat(files).allMatch(file -> file.getName().endsWith(".ocl"));
  }

  @Test
  void rejectsConstraintsOfUnknownMetamodels() {
    assertThatThrownBy(
            () ->
                service.buildProjectJar(
                    List.of(metamodel()),
                    List.of(genmodel()),
                    List.of(reaction()),
                    List.of(constraint("shop.ocl", "context shop::Item inv a: true"))))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("shop")
        .hasMessageContaining("shop.ocl");
  }

  @Test
  void rejectsEmptyConstraintFiles() {
    assertThatThrownBy(
            () ->
                service.buildProjectJar(
                    List.of(metamodel()),
                    List.of(genmodel()),
                    List.of(reaction()),
                    List.of(constraint("empty.ocl", ""))))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("constraint file");
  }

  private static MockMultipartFile constraint(String name, String content) {
    return new MockMultipartFile(
        "constraintFiles", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile reaction() {
    return new MockMultipartFile(
        "reactionFiles",
        "sample.reactions",
        "text/plain",
        "import \"pcm\"\n".getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile genmodel() {
    return new MockMultipartFile(
        "genmodelFiles",
        "model.genmodel",
        "application/xml",
        "content".getBytes(StandardCharsets.UTF_8));
  }

  private static MockMultipartFile metamodel() {
    return new MockMultipartFile(
        "metamodelFiles",
        "model.ecore",
        "application/xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <ecore:EPackage xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
            name="pcm" nsURI="http://pcm" nsPrefix="pcm"/>
        """
            .getBytes(StandardCharsets.UTF_8));
  }
}
