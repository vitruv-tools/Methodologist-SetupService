package tools.vitruv.methodologist.setup.model.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.vitruv.methodologist.setup.config.GlobalExceptionHandler;
import tools.vitruv.methodologist.setup.exception.GenmodelException;
import tools.vitruv.methodologist.setup.model.service.EcoreToGenmodelService;
import tools.vitruv.methodologist.setup.model.service.GenmodelFileService;
import tools.vitruv.methodologist.setup.model.service.GenmodelPrecheckService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenModel Generate Endpoint Tests")
class GenmodelGenerateControllerTest {

  private MockMvc mockMvc;
  private EcoreToGenmodelService ecoreToGenmodelService;
  private GenmodelFileService genmodelFileService;
  private GenmodelPrecheckService genmodelPrecheckService;

  @BeforeEach
  void setUp() {
    ecoreToGenmodelService = mock(EcoreToGenmodelService.class);
    genmodelFileService = mock(GenmodelFileService.class);
    genmodelPrecheckService = mock(GenmodelPrecheckService.class);

    GenmodelController controller =
        new GenmodelController(
            genmodelPrecheckService, genmodelFileService, ecoreToGenmodelService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("Should generate genmodel from valid ecore file")
  void testGenerateGenmodelSuccess() throws Exception {
    String ecoreContent =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<ecore:EPackage xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\"\n"
            + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" name=\"testmodel\"\n"
            + "    nsURI=\"http://example.com/testmodel\" nsPrefix=\"tm\">\n"
            + "</ecore:EPackage>";
    byte[] ecoreBytes = ecoreContent.getBytes(StandardCharsets.UTF_8);
    byte[] genmodelContent = "<?xml version=\"1.0\"?><genmodel/>".getBytes(StandardCharsets.UTF_8);

    when(genmodelFileService.multipartToBytes(any())).thenReturn(ecoreBytes);
    when(ecoreToGenmodelService.generateGenmodelFromEcore(any(byte[].class), anyString()))
        .thenReturn(genmodelContent);

    MockMultipartFile file =
        new MockMultipartFile("file", "testmodel.ecore", "application/octet-stream", ecoreBytes);

    mockMvc
        .perform(multipart("/api/genmodel/generate").file(file))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(header().exists("Content-Disposition"))
        .andExpect(content().bytes(genmodelContent));
  }

  @Test
  @DisplayName("Should return error when file is empty")
  void testGenerateGenmodelEmptyFile() throws Exception {
    when(genmodelFileService.multipartToBytes(any()))
        .thenThrow(new GenmodelException("EMPTY_FILE", "Uploaded file is empty"));

    MockMultipartFile file =
        new MockMultipartFile("file", "empty.ecore", "application/octet-stream", new byte[0]);

    mockMvc
        .perform(multipart("/api/genmodel/generate").file(file))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("Should return error when genmodel generation fails")
  void testGenerateGenmodelGenerationFailure() throws Exception {
    String ecoreContent =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<ecore:EPackage xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\"\n"
            + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" name=\"testmodel\"\n"
            + "    nsURI=\"http://example.com/testmodel\" nsPrefix=\"tm\">\n"
            + "</ecore:EPackage>";
    byte[] ecoreBytes = ecoreContent.getBytes(StandardCharsets.UTF_8);

    when(genmodelFileService.multipartToBytes(any())).thenReturn(ecoreBytes);
    when(ecoreToGenmodelService.generateGenmodelFromEcore(any(byte[].class), anyString()))
        .thenThrow(
            new GenmodelException(
                "GENMODEL_GENERATION_ERROR", "Failed to generate genmodel from ecore file"));

    MockMultipartFile file =
        new MockMultipartFile("file", "invalid.ecore", "application/octet-stream", ecoreBytes);

    mockMvc
        .perform(multipart("/api/genmodel/generate").file(file))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("Should handle ecore filename without extension")
  void testGenerateGenmodelNoExtension() throws Exception {
    String ecoreContent =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<ecore:EPackage xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\"\n"
            + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "    xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" name=\"testmodel\"\n"
            + "    nsURI=\"http://example.com/testmodel\" nsPrefix=\"tm\">\n"
            + "</ecore:EPackage>";
    byte[] ecoreBytes = ecoreContent.getBytes(StandardCharsets.UTF_8);
    byte[] genmodelContent = "<?xml version=\"1.0\"?><genmodel/>".getBytes(StandardCharsets.UTF_8);

    when(genmodelFileService.multipartToBytes(any())).thenReturn(ecoreBytes);
    when(ecoreToGenmodelService.generateGenmodelFromEcore(any(byte[].class), anyString()))
        .thenReturn(genmodelContent);

    MockMultipartFile file =
        new MockMultipartFile("file", "testmodel", "application/octet-stream", ecoreBytes);

    mockMvc
        .perform(multipart("/api/genmodel/generate").file(file))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString("testmodel.genmodel")));
  }
}
