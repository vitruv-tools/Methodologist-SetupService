package tools.vitruv.methodologist.setup.exception;

import org.springframework.http.HttpStatus;

/** Base exception for Methodologist Setup Service. */
public class MethodologistSetupException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private final String errorCode;
  private final HttpStatus status;

  /**
   * Constructs a MethodologistSetupException reported as {@code 400 Bad Request}.
   *
   * @param errorCode the error code
   * @param message the error message
   */
  public MethodologistSetupException(String errorCode, String message) {
    this(errorCode, message, HttpStatus.BAD_REQUEST, null);
  }

  /**
   * Constructs a MethodologistSetupException with a cause, reported as {@code 400 Bad Request}.
   *
   * @param errorCode the error code
   * @param message the error message
   * @param cause the underlying cause
   */
  public MethodologistSetupException(String errorCode, String message, Throwable cause) {
    this(errorCode, message, HttpStatus.BAD_REQUEST, cause);
  }

  /**
   * Constructs a MethodologistSetupException with an explicit HTTP status.
   *
   * @param errorCode the error code
   * @param message the error message
   * @param status the HTTP status reported to the client
   * @param cause the underlying cause, may be {@code null}
   */
  public MethodologistSetupException(
      String errorCode, String message, HttpStatus status, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.status = status;
  }

  /**
   * Gets the error code.
   *
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Gets the HTTP status reported to the client.
   *
   * @return the HTTP status
   */
  public HttpStatus getStatus() {
    return status;
  }
}
