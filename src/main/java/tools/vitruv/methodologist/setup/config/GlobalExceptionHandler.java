package tools.vitruv.methodologist.setup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
public class GlobalExceptionHandler {
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Handles MethodologistSetupException.
   *
   * @param ex the exception
   * @param request the web request
   */
  @ExceptionHandler(MethodologistSetupException.class)
  public void handleMethodologistSetupException(
      MethodologistSetupException ex, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    log.error("MethodologistSetupException occurred: {}", ex.getMessage(), ex);

    writeErrorResponse(
        response,
        request,
        HttpStatus.BAD_REQUEST,
        ex.getErrorCode(),
        ex.getMessage());
  }

  /**
   * Handles GenmodelException.
   *
   * @param ex the exception
   * @param request the web request
   */
  @ExceptionHandler(GenmodelException.class)
  public void handleGenmodelException(
      GenmodelException ex, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    log.error("GenmodelException occurred: {}", ex.getMessage(), ex);

    writeErrorResponse(
        response,
        request,
        HttpStatus.UNPROCESSABLE_ENTITY,
        ex.getErrorCode(),
        ex.getMessage());
  }

  /**
   * Handles NoSuchFileException raised when an expected build artifact is missing.
   *
   * @param ex the exception
   * @param request the web request
   */
  @ExceptionHandler(NoSuchFileException.class)
  public void handleNoSuchFileException(
      NoSuchFileException ex, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    log.error("NoSuchFileException occurred: {}", ex.getMessage(), ex);

    writeErrorResponse(
        response,
        request,
        HttpStatus.BAD_REQUEST,
        "VSUM_ARTIFACT_NOT_FOUND",
        ex.getMessage());
  }

  /**
   * Handles all other exceptions.
   *
   * @param ex the exception
   * @param request the web request
   */
  @ExceptionHandler(Exception.class)
  public void handleGlobalException(
      Exception ex, HttpServletRequest request, HttpServletResponse response) throws IOException {
    log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);

    writeErrorResponse(
        response,
        request,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        ErrorMessages.UNEXPECTED_ERROR);
  }

  private void writeErrorResponse(
      HttpServletResponse response,
      HttpServletRequest request,
      HttpStatus status,
      String errorCode,
      String message)
      throws IOException {
    ErrorResponseDTO errorResponse =
        ErrorResponseDTO.builder()
            .errorCode(errorCode)
            .message(message)
            .status(status.value())
            .timestamp(System.currentTimeMillis())
            .path(request.getRequestURI())
            .build();

    response.setStatus(status.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
