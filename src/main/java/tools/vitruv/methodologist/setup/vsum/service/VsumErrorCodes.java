package tools.vitruv.methodologist.setup.vsum.service;

/** Error codes reported by the VSUM build pipeline. */
public final class VsumErrorCodes {

  /** Uploaded files are missing, empty, misnamed, or otherwise unusable. */
  public static final String INPUT_ERROR = "VSUM_INPUT_ERROR";

  /** An uploaded metamodel cannot be loaded or is not a valid Ecore model. */
  public static final String METAMODEL_ERROR = "VSUM_METAMODEL_ERROR";

  /** An uploaded reactions file does not fit the uploaded metamodels. */
  public static final String REACTIONS_ERROR = "VSUM_REACTIONS_ERROR";

  /** The generated project was rejected by the Maven build (compilation or smoke test failed). */
  public static final String BUILD_FAILED = "VSUM_BUILD_FAILED";

  /** The Maven build did not finish within the configured timeout. */
  public static final String BUILD_TIMEOUT = "VSUM_BUILD_TIMEOUT";

  /** An infrastructure problem (file system, process start, interruption) prevented the build. */
  public static final String BUILD_ERROR = "VSUM_BUILD_ERROR";

  private VsumErrorCodes() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
