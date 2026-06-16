package tools.vitruv.methodologist.setup.vsum.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.VsumProjectBuildService;
import tools.vitruv.methodologist.setup.vsum.service.VsumService;

/** Unit tests for VSUM project build orchestration logic. */
@ExtendWith(MockitoExtension.class)
class VsumControllerTest {

  @Mock private VsumService vsumService;

  @InjectMocks private VsumProjectBuildService vsumProjectBuildService;

  /** Verifies uploaded files are transformed and delegated to VSUM generation service. */
  @Test
  void buildProjectArchiveDelegatesToVsumService() throws Exception {
    byte[] archive = "zip-content".getBytes();
    when(vsumService.generateProjectArchive(anyList(), anyList(), any())).thenReturn(archive);

    MockMultipartFile metamodel =
        new MockMultipartFile(
            "metamodelFiles", "model.ecore", "application/octet-stream", "meta".getBytes());
    MockMultipartFile genmodel =
        new MockMultipartFile(
            "genmodelFiles", "model.genmodel", "application/octet-stream", "gen".getBytes());

    byte[] result =
        vsumProjectBuildService.buildProjectArchive(List.of(metamodel), List.of(genmodel), List.of());

    assertArrayEquals(archive, result);
    verify(vsumService).generateProjectArchive(anyList(), anyList(), any());
  }

  /** Ensures mismatched metamodel/genmodel upload counts are rejected. */
  @Test
  void buildProjectArchiveRejectsMismatchedPairs() {
    MockMultipartFile metamodel =
        new MockMultipartFile(
            "metamodelFiles", "model.ecore", "application/octet-stream", "meta".getBytes());

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () ->
                vsumProjectBuildService.buildProjectArchive(
                    List.of(metamodel), List.of(), List.of()));

    org.junit.jupiter.api.Assertions.assertEquals("VSUM_INPUT_ERROR", exception.getErrorCode());
  }

  /** Verifies reaction imports are rewritten to the uploaded metamodel nsURI when needed. */
  @Test
  void buildProjectArchiveNormalizesReactionImportUris() throws Exception {
    byte[] archive = "zip-content".getBytes();
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<java.io.File> reactionFiles = invocation.getArgument(1);
              String rewrittenContent = Files.readString(reactionFiles.getFirst().toPath());
              assertTrue(rewrittenContent.contains("http://example.org/model"));
              return archive;
            })
        .when(vsumService)
        .generateProjectArchive(anyList(), anyList(), any());

    String ecore =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\""
            + " name=\"model\" nsURI=\"http://example.org/model\" nsPrefix=\"model\"/>";
    String reaction = "import \"http://vitruv.tools/methodologisttemplate/model\" as m";

    MockMultipartFile metamodel =
        new MockMultipartFile("metamodelFiles", "model.ecore", "application/xml", ecore.getBytes());
    MockMultipartFile genmodel =
        new MockMultipartFile(
            "genmodelFiles", "model.genmodel", "application/xml", "genmodel".getBytes());
    MockMultipartFile reactionFile =
        new MockMultipartFile("reactionFiles", "rules.reactions", "text/plain", reaction.getBytes());

    vsumProjectBuildService.buildProjectArchive(
        List.of(metamodel), List.of(genmodel), List.of(reactionFile));

    verify(vsumService).generateProjectArchive(anyList(), anyList(), any());
  }
}

