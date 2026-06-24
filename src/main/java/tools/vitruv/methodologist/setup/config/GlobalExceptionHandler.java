package tools.vitruv.methodologist.setup.config;

import java.nio.file.NoSuchFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import tools.vitruv.methodologist.setup.exception.ErrorResponseDTO;
import tools.vitruv.methodologist.setup.exception.GenmodelException;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.messages.ErrorMessages;

/**
 * Global exception handler for the application. Catches all exceptions and returns appropriate
 * error responses.
 */
@Slf4j
@RestControllerAdvice(basePackages = "tools.vitruv.methodologist.setup")
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  /**
   * Handles MethodologistSetupException.
   *
   * @param ex the exception
   * @param request the web request
   * @return error response
   */
  @ExceptionHandler(MethodologistSetupException.class)
  public ResponseEntity<ErrorResponseDTO> handleMethodologistSetupException(
      MethodologistSetupException ex, WebRequest request) {
    log.error("MethodologistSetupException occurred: {}", ex.getMessage(), ex);

    ErrorResponseDTO errorResponse =
        ErrorResponseDTO.builder()
            .errorCode(ex.getErrorCode())
            .message(ex.getMessage())
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(System.currentTimeMillis())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  /**
   * Handles GenmodelException.
   *
   * @param ex the exception
   * @param request the web request
   * @return error response
   */
  @ExceptionHandler(GenmodelException.class)
  public ResponseEntity<ErrorResponseDTO> handleGenmodelException(
      GenmodelException ex, WebRequest request) {
    log.error("GenmodelException occurred: {}", ex.getMessage(), ex);

    ErrorResponseDTO errorResponse =
        ErrorResponseDTO.builder()
            .errorCode(ex.getErrorCode())
            .message(ex.getMessage())
            .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .timestamp(System.currentTimeMillis())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

    return new ResponseEntity<>(errorResponse, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /**
   * Handles NoSuchFileException raised when an expected build artifact is missing.
   *
   * @param ex the exception
   * @param request the web request
   * @return error response with bad request status
   */
  @ExceptionHandler(NoSuchFileException.class)
  public ResponseEntity<ErrorResponseDTO> handleNoSuchFileException(
      NoSuchFileException ex, WebRequest request) {
    log.error("NoSuchFileException occurred: {}", ex.getMessage(), ex);

    ErrorResponseDTO errorResponse =
        ErrorResponseDTO.builder()
            .errorCode("VSUM_ARTIFACT_NOT_FOUND")
            .message(ex.getMessage())
            .status(HttpStatus.BAD_REQUEST.value())
            .timestamp(System.currentTimeMillis())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  /**
   * Handles all other exceptions.
   *
   * @param ex the exception
   * @param request the web request
   * @return error response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDTO> handleGlobalException(Exception ex, WebRequest request) {
    log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);

    ErrorResponseDTO errorResponse =
        ErrorResponseDTO.builder()
            .errorCode("INTERNAL_ERROR")
            .message(ErrorMessages.UNEXPECTED_ERROR)
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .timestamp(System.currentTimeMillis())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();

    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
