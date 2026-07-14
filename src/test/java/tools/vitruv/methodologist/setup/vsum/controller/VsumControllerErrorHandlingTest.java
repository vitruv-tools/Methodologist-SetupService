package tools.vitruv.methodologist.setup.vsum.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.vitruv.methodologist.setup.config.GlobalExceptionHandler;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.VsumProjectBuildService;

@ExtendWith(MockitoExtension.class)
class VsumControllerErrorHandlingTest {

  @Test
  void buildProject_shouldReturnJsonErrorForZipAcceptHeader() throws Exception {
    VsumProjectBuildService buildService = mock(VsumProjectBuildService.class);
    VsumController controller = new VsumController(buildService);
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    when(buildService.buildProjectArchive(anyList(), anyList(), anyList()))
        .thenThrow(
            new MethodologistSetupException(
                "VSUM_INPUT_ERROR",
                "Please upload the same number of metamodel and genmodel files"));

    MockMultipartFile metamodelFile =
        new MockMultipartFile(
            "metamodelFiles", "a.ecore", "application/xml", "<ecore/>".getBytes());
    MockMultipartFile genmodelFile =
        new MockMultipartFile(
            "genmodelFiles", "a.genmodel", "application/xml", "<genmodel/>".getBytes());
    MockMultipartFile reactionFile =
        new MockMultipartFile(
            "reactionFiles", "a.reactions", "text/plain", "import \"x\"".getBytes());

    mockMvc
        .perform(
            multipart("/api/vsum/build")
                .file(metamodelFile)
                .file(genmodelFile)
                .file(reactionFile)
                .accept(MediaType.parseMediaType("application/zip")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("VSUM_INPUT_ERROR"))
        .andExpect(
            jsonPath("$.message")
                .value("Please upload the same number of metamodel and genmodel files"));
  }
}
