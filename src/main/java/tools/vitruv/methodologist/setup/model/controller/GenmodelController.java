package tools.vitruv.methodologist.setup.model.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.ResponseTemplateDto;
import tools.vitruv.methodologist.setup.messages.InfoMessages;
import tools.vitruv.methodologist.setup.model.controller.dto.request.GenmodelIssueDTO;
import tools.vitruv.methodologist.setup.model.service.GenmodelFileService;
import tools.vitruv.methodologist.setup.model.service.GenmodelPrecheckService;
import tools.vitruv.methodologist.setup.model.service.GenmodelPrecheckService.GenmodelIssue;
import tools.vitruv.methodologist.setup.model.service.GenmodelPrecheckService.ProcessResult;

/** REST controller for GenModel validation and processing operations. */
@Slf4j
@RestController
@RequestMapping("/api/genmodel")
@Tag(name = "GenModel", description = "GenModel validation and processing endpoints")
public class GenmodelController {

  private final GenmodelPrecheckService genmodelPrecheckService;
  private final GenmodelFileService genmodelFileService;

  /**
   * Constructs a GenmodelController with the required services.
   *
   * @param genmodelPrecheckService the genmodel precheck service
   * @param genmodelFileService the genmodel file service
   */
  @Autowired
  public GenmodelController(
      GenmodelPrecheckService genmodelPrecheckService, GenmodelFileService genmodelFileService) {
    this.genmodelPrecheckService = genmodelPrecheckService;
    this.genmodelFileService = genmodelFileService;
  }

  /**
   * Inspects a GenModel file and reports the changes that would be applied.
   *
   * @param file the GenModel file to inspect
   * @return response containing detected issues and planned changes
   */
  @PostMapping(value = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Inspect GenModel file",
      description = "Preview changes without modifying the file",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "File inspected successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ResponseTemplateDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid file"),
        @ApiResponse(responseCode = "500", description = "Server error")
      })
  public ResponseEntity<ResponseTemplateDto<List<GenmodelIssueDTO>>> inspect(
      @RequestPart("file") MultipartFile file) {
    log.info("Inspecting GenModel file: {}", file.getOriginalFilename());

    byte[] fileBytes = genmodelFileService.multipartToBytes(file);
    List<GenmodelIssue> issues =
        genmodelPrecheckService.inspectFileBytes(fileBytes, file.getOriginalFilename());

    List<GenmodelIssueDTO> issueDTOs = convertToDTO(issues);
    ResponseTemplateDto<List<GenmodelIssueDTO>> response =
        ResponseTemplateDto.<List<GenmodelIssueDTO>>builder()
            .data(issueDTOs)
            .message(InfoMessages.GENMODEL_INSPECTION_SUCCESS)
            .build();

    return ResponseEntity.ok(response);
  }

  /**
   * Processes a GenModel file and returns the standardized version.
   *
   * @param file the GenModel file to process
   * @return the processed file as a downloadable attachment
   */
  @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Process GenModel file",
      description = "Validate and apply standardization changes, returning the processed file",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "File processed successfully",
            content = @Content(mediaType = "application/octet-stream")),
        @ApiResponse(responseCode = "400", description = "Invalid file"),
        @ApiResponse(responseCode = "500", description = "Server error")
      })
  public ResponseEntity<byte[]> process(
      @Parameter(
              description = "The GenModel file to process",
              required = true,
              schema = @Schema(type = "string", format = "binary"))
          @RequestPart("file")
          MultipartFile file) {

    byte[] fileBytes = genmodelFileService.multipartToBytes(file);
    ProcessResult result =
        genmodelPrecheckService.processFileBytes(fileBytes, file.getOriginalFilename());

    String filename = generateProcessedFilename(file.getOriginalFilename());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
    headers.setContentLength(result.processedContent.length);

    log.info("Returning processed file: {}", filename);
    return ResponseEntity.ok().headers(headers).body(result.processedContent);
  }

  /**
   * Converts GenmodelIssue objects to GenmodelIssueDTO objects.
   *
   * @param issues the list of issues to convert
   * @return the list of issue DTOs
   */
  private List<GenmodelIssueDTO> convertToDTO(List<GenmodelIssue> issues) {
    return issues.stream()
        .map(issue -> new GenmodelIssueDTO(issue.filename, issue.message))
        .collect(Collectors.toList());
  }

  /**
   * Generates a processed filename with timestamp.
   *
   * @param originalFilename the original filename
   * @return the processed filename
   */
  private String generateProcessedFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isEmpty()) {
      return "processed_" + System.currentTimeMillis() + ".genmodel";
    }

    String nameWithoutExt =
        originalFilename.contains(".")
            ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
            : originalFilename;
    return nameWithoutExt + "_processed.genmodel";
  }
}
