package tools.vitruv.methodologist.setup.vsum.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.vitruv.methodologist.setup.config.GlobalExceptionHandler;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.VsumProjectBuildService;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;

class VsumControllerErrorHandlingTest {

  private static final MockMultipartFile METAMODEL =
      new MockMultipartFile("metamodelFiles", "a.ecore", "application/xml", "<ecore/>".getBytes());
  private static final MockMultipartFile REACTIONS =
      new MockMultipartFile(
          "reactionFiles", "a.reactions", "text/plain", "reactions: a".getBytes());

  private VsumProjectBuildService buildService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    buildService = mock(VsumProjectBuildService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new VsumController(buildService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void buildPassesAllUploadsToTheBuildService() throws Exception {
    when(buildService.buildProjectArchive(any())).thenReturn("zip".getBytes());
    MockMultipartFile constraints =
        new MockMultipartFile("constraintFiles", "c.ocl", "text/plain", "context".getBytes());
    MockMultipartFile view =
        new MockMultipartFile("viewFiles", "v.nj", "text/plain", "view".getBytes());

    mockMvc
        .perform(
            multipart("/api/vsum/build")
                .file(METAMODEL)
                .file(REACTIONS)
                .file(constraints)
                .file(view))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/zip"))
        .andExpect(content().bytes("zip".getBytes()));

    verify(buildService)
        .buildProjectArchive(
            argThat(
                request ->
                    request.metamodels().get(0).fileName().equals("a.ecore")
                        && request.reactions().get(0).fileName().equals("a.reactions")
                        && request.constraints().get(0).fileName().equals("c.ocl")
                        && request.viewDefinitions().get(0).fileName().equals("v.nj")));
  }

  @Test
  void reactionsConstraintsAndViewsAreOptional() throws Exception {
    when(buildService.buildProjectJar(any())).thenReturn("jar".getBytes());

    mockMvc
        .perform(multipart("/api/vsum/jar").file(METAMODEL))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/java-archive"));
  }

  @Test
  void missingMetamodelsAreABadRequest() throws Exception {
    mockMvc
        .perform(multipart("/api/vsum/build").file(REACTIONS))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    verifyNoInteractions(buildService);
  }

  @Test
  void uploadsWithTheWrongExtensionAreRejectedBeforeBuilding() throws Exception {
    MockMultipartFile genmodel =
        new MockMultipartFile("metamodelFiles", "a.genmodel", "application/xml", "<g/>".getBytes());

    mockMvc
        .perform(multipart("/api/vsum/build").file(genmodel))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value(VsumErrorCodes.INPUT_ERROR));
    verifyNoInteractions(buildService);
  }

  @Test
  void buildProblemsAreIncludedInTheErrorResponse() throws Exception {
    when(buildService.buildProjectJar(any()))
        .thenThrow(
            new VsumBuildException(
                VsumErrorCodes.BUILD_FAILED,
                "The VSUM build failed:",
                List.of(
                    BuildProblem.at(
                            Source.REACTIONS,
                            "a.reactions",
                            "import java.io.File\n",
                            1,
                            8,
                            "mismatched input 'java' expecting RULE_STRING")
                        .withHiddenFollowUpErrors(2)),
                HttpStatus.UNPROCESSABLE_CONTENT));

    mockMvc
        .perform(multipart("/api/vsum/jar").file(METAMODEL))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value(VsumErrorCodes.BUILD_FAILED))
        .andExpect(jsonPath("$.problems[0].source").value("REACTIONS"))
        .andExpect(jsonPath("$.problems[0].file").value("a.reactions"))
        .andExpect(jsonPath("$.problems[0].line").value(1))
        .andExpect(jsonPath("$.problems[0].column").value(8))
        .andExpect(jsonPath("$.problems[0].excerpt").value("import java.io.File\n       ^"))
        .andExpect(jsonPath("$.problems[0].hiddenFollowUpErrors").value(2))
        .andExpect(jsonPath("$.problems[0].hint").doesNotExist())
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.containsString("line 1, column 8: mismatched")));
  }

  @Test
  void otherErrorsHaveNoProblems() throws Exception {
    mockMvc
        .perform(multipart("/api/vsum/jar").file(REACTIONS))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.problems").doesNotExist());
  }

  @Test
  void buildFailuresUseTheStatusOfTheException() throws Exception {
    when(buildService.buildProjectArchive(any()))
        .thenThrow(
            new MethodologistSetupException(
                VsumErrorCodes.BUILD_FAILED,
                "The VSUM build failed:\n[ERROR] boom",
                HttpStatus.UNPROCESSABLE_CONTENT,
                null));

    mockMvc
        .perform(
            multipart("/api/vsum/build")
                .file(METAMODEL)
                .accept(MediaType.parseMediaType("application/zip")))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value(VsumErrorCodes.BUILD_FAILED))
        .andExpect(jsonPath("$.message").value("The VSUM build failed:\n[ERROR] boom"));
  }
}
