package tools.vitruv.methodologist.setup.exception;

/** Exception thrown when a GenModel file is invalid or not found. */
public class GenmodelException extends MethodologistSetupException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a GenmodelException.
   *
   * @param errorCode the error code
   * @param message the error message
   */
  public GenmodelException(String errorCode, String message) {
    super(errorCode, message);
  }

  /**
   * Constructs a GenmodelException with a cause.
   *
   * @param errorCode the error code
   * @param message the error message
   * @param cause the underlying cause
   */
  public GenmodelException(String errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }
}
