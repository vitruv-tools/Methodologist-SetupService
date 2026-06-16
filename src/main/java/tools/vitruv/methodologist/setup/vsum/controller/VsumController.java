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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumProjectBuildService;

/** REST API for VSUM project generation and packaging. */
@RestController
@RequestMapping("/api/vsum")
@RequiredArgsConstructor
@Tag(name = "VSUM", description = "VSUM project generation endpoints")
public class VsumController {

  private final VsumProjectBuildService vsumProjectBuildService;

  /**
   * Accepts model and reaction files, builds a VSUM project, and returns the built project zip.
   *
   * @param metamodelFiles metamodel files
   * @param genmodelFiles genmodel files paired by index with metamodel files
   * @param reactionFiles optional reaction files
   * @return zip file response containing generated project
   */
  @GetMapping(value = "/build", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Build VSUM project",
      description =
          "Uploads metamodel/genmodel files, builds the project from templates, and returns a zip archive",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Project built successfully",
            content = @Content(mediaType = "application/zip")),
        @ApiResponse(responseCode = "400", description = "Invalid input files"),
        @ApiResponse(responseCode = "500", description = "Build failed")
      })
  public ResponseEntity<byte[]> buildProject(
      @Parameter(description = "Metamodel files", required = true)
          @RequestPart("metamodelFiles")
          List<MultipartFile> metamodelFiles,
      @Parameter(description = "Genmodel files paired by index with metamodel files", required = true)
          @RequestPart("genmodelFiles")
          List<MultipartFile> genmodelFiles,
      @Parameter(description = "Optional reaction files")
          @RequestPart(value = "reactionFiles", required = false)
          List<MultipartFile> reactionFiles) {

    byte[] archive =
        vsumProjectBuildService.buildProjectArchive(metamodelFiles, genmodelFiles, reactionFiles);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/zip"));
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename(generateArchiveFilename(), StandardCharsets.UTF_8)
            .build());
    headers.setContentLength(archive.length);

    return ResponseEntity.ok().headers(headers).body(archive);
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

