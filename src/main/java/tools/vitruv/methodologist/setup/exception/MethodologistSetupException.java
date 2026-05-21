package tools.vitruv.methodologist.setup.exception;

/** Base exception for Methodologist Setup Service. */
public class MethodologistSetupException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private final String errorCode;

  /**
   * Constructs a MethodologistSetupException.
   *
   * @param errorCode the error code
   * @param message the error message
   */
  public MethodologistSetupException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * Constructs a MethodologistSetupException with a cause.
   *
   * @param errorCode the error code
   * @param message the error message
   * @param cause the underlying cause
   */
  public MethodologistSetupException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  /**
   * Gets the error code.
   *
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }
}
