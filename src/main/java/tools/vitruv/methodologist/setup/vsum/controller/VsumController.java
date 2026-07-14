package tools.vitruv.methodologist.setup.vsum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
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
import tools.vitruv.methodologist.setup.vsum.service.VsumProjectBuildService;

/** REST API for VSUM project generation and packaging. */
@RestController
@RequestMapping("/api/vsum")
@RequiredArgsConstructor
@Tag(name = "VSUM", description = "VSUM project generation endpoints")
public class VsumController {

  /** Download filename for the executable VSUM jar-with-dependencies. */
  private static final String VSUM_JAR_FILENAME =
      "tools.vitruv.methodologisttemplate.vsum-0.1.0-SNAPSHOT-jar-with-dependencies.jar";

  private final VsumProjectBuildService vsumProjectBuildService;

  /**
   * Accepts model and reaction files, builds a VSUM project, and returns the built project zip.
   *
   * @param metamodelFiles metamodel files
   * @param genmodelFiles genmodel files paired by index with metamodel files
   * @param reactionFiles reaction files
   * @return zip file response containing generated project
   * @throws NoSuchFileException when the build does not produce the expected artifact
   */
  @PostMapping(value = "/build", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Build VSUM project",
      description =
          "Uploads metamodel/genmodel files,"
              + " builds the project from templates,"
              + " and returns a zip archive",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Project built successfully",
            content = @Content(mediaType = "application/zip")),
        @ApiResponse(responseCode = "400", description = "Invalid input files"),
        @ApiResponse(responseCode = "500", description = "Build failed")
      })
  public ResponseEntity<byte[]> buildProject(
      @Parameter(description = "Metamodel files", required = true) @RequestPart("metamodelFiles")
          List<MultipartFile> metamodelFiles,
      @Parameter(
              description = "Genmodel files paired by index with metamodel files",
              required = true)
          @RequestPart("genmodelFiles")
          List<MultipartFile> genmodelFiles,
      @Parameter(description = "Reaction files", required = true) @RequestPart("reactionFiles")
          List<MultipartFile> reactionFiles)
      throws NoSuchFileException {

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
   * Accepts model and reaction files, builds a VSUM project, and returns only the executable VSUM
   * jar-with-dependencies produced under {@code vsum/target}, instead of the whole project archive.
   *
   * @param metamodelFiles metamodel files
   * @param genmodelFiles genmodel files paired by index with metamodel files
   * @param reactionFiles reaction files
   * @return response containing the built VSUM jar
   */
  @PostMapping(value = "/jar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Build VSUM jar",
      description =
          "Uploads metamodel/genmodel files,"
              + " builds the project from templates,"
              + " and returns only the executable VSUM jar-with-dependencies",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Jar built successfully",
            content = @Content(mediaType = "application/java-archive")),
        @ApiResponse(responseCode = "400", description = "Invalid input files"),
        @ApiResponse(responseCode = "500", description = "Build failed")
      })
  public ResponseEntity<byte[]> buildJar(
      @Parameter(description = "Metamodel files", required = true) @RequestPart("metamodelFiles")
          List<MultipartFile> metamodelFiles,
      @Parameter(
              description = "Genmodel files paired by index with metamodel files",
              required = true)
          @RequestPart("genmodelFiles")
          List<MultipartFile> genmodelFiles,
      @Parameter(description = "Reaction files", required = true) @RequestPart("reactionFiles")
          List<MultipartFile> reactionFiles) {

    byte[] jar =
        vsumProjectBuildService.buildProjectJar(metamodelFiles, genmodelFiles, reactionFiles);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/java-archive"));
    headers.setContentDisposition(
        ContentDisposition.attachment()
            .filename(VSUM_JAR_FILENAME, StandardCharsets.UTF_8)
            .build());
    headers.setContentLength(jar.length);

    return ResponseEntity.ok().headers(headers).body(jar);
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
