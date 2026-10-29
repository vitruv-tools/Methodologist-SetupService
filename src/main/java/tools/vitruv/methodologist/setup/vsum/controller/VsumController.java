package tools.vitruv.methodologist.setup.vsum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumBuildRequest;
import tools.vitruv.methodologist.setup.vsum.service.VsumProjectBuildService;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/** REST API for VSUM project generation and packaging. */
@RestController
@RequestMapping("/api/vsum")
@RequiredArgsConstructor
@Tag(name = "VSUM", description = "VSUM project generation endpoints")
public class VsumController {

  private final VsumProjectBuildService vsumProjectBuildService;

  /**
   * Generates a VSUM project following the Methodologist Template from the uploaded files and
   * returns it as a zip archive, without building it.
   *
   * @param metamodelFiles Ecore metamodels
   * @param reactionFiles Reactions consistency specifications
   * @param constraintFiles VitruvOCL constraints
   * @param viewFiles NeoJoin view definitions
   * @return zip file response containing the generated project
   */
  @PostMapping(value = "/project", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Generate VSUM project",
      description =
          "Validates the inputs, generates genmodels from the metamodels and a VSUM project"
              + " following the Methodologist Template, and returns the project sources as a zip"
              + " archive. The project is not built; build it with 'mvn clean verify'",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Project generated successfully",
            content = @Content(mediaType = "application/zip")),
        @ApiResponse(responseCode = "400", description = "Invalid input files")
      })
  public ResponseEntity<byte[]> generateProject(
      @Parameter(description = "Ecore metamodel files", required = true)
          @RequestPart("metamodelFiles")
          List<MultipartFile> metamodelFiles,
      @Parameter(description = "Reactions files")
          @RequestPart(value = "reactionFiles", required = false)
          List<MultipartFile> reactionFiles,
      @Parameter(description = "VitruvOCL constraint files")
          @RequestPart(value = "constraintFiles", required = false)
          List<MultipartFile> constraintFiles,
      @Parameter(description = "NeoJoin view definitions (stored, not yet compiled)")
          @RequestPart(value = "viewFiles", required = false)
          List<MultipartFile> viewFiles) {
    VsumBuildRequest request =
        VsumBuildRequest.fromMultipart(metamodelFiles, reactionFiles, constraintFiles, viewFiles);
    byte[] archive = vsumProjectBuildService.generateProjectArchive(request);
    return attachment(archive, "application/zip", generateArchiveFilename());
  }

  /**
   * Builds a VSUM from the uploaded files and returns its executable jar-with-dependencies.
   *
   * @param metamodelFiles Ecore metamodels
   * @param reactionFiles Reactions consistency specifications
   * @param constraintFiles VitruvOCL constraints
   * @param viewFiles NeoJoin view definitions
   * @return response containing the built VSUM jar
   */
  @PostMapping(value = "/jar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Build VSUM jar",
      description =
          "Generates the project like /project, builds and smoke-tests it with Maven, and returns"
              + " the executable VSUM jar-with-dependencies, which hosts the VSUM in a Vitruvius"
              + " server",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Jar built successfully",
            content = @Content(mediaType = "application/java-archive")),
        @ApiResponse(responseCode = "400", description = "Invalid input files"),
        @ApiResponse(responseCode = "422", description = "The generated VSUM does not build"),
        @ApiResponse(responseCode = "504", description = "The build timed out")
      })
  public ResponseEntity<byte[]> buildJar(
      @Parameter(description = "Ecore metamodel files", required = true)
          @RequestPart("metamodelFiles")
          List<MultipartFile> metamodelFiles,
      @Parameter(description = "Reactions files")
          @RequestPart(value = "reactionFiles", required = false)
          List<MultipartFile> reactionFiles,
      @Parameter(description = "VitruvOCL constraint files")
          @RequestPart(value = "constraintFiles", required = false)
          List<MultipartFile> constraintFiles,
      @Parameter(description = "NeoJoin view definitions (stored, not yet compiled)")
          @RequestPart(value = "viewFiles", required = false)
          List<MultipartFile> viewFiles) {
    VsumBuildRequest request =
        VsumBuildRequest.fromMultipart(metamodelFiles, reactionFiles, constraintFiles, viewFiles);
    byte[] jar = vsumProjectBuildService.buildProjectJar(request);
    return attachment(jar, "application/java-archive", ProjectLayout.VSUM_JAR_FILENAME);
  }

  private ResponseEntity<byte[]> attachment(byte[] content, String mediaType, String filename) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(mediaType));
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
    headers.setContentLength(content.length);
    return ResponseEntity.ok().headers(headers).body(content);
  }

  /**
   * Creates a deterministic archive filename prefix for build downloads.
   *
   * @return zip filename
   */
  private String generateArchiveFilename() {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    return "vsum-project-" + timestamp + ".zip";
  }
}
