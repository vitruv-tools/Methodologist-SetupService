package tools.vitruv.methodologist.setup.vsum.service;

/** Error codes reported by the VSUM build pipeline. */
public final class VsumErrorCodes {

  /** Uploaded files are missing, empty, misnamed, or otherwise unusable. */
  public static final String INPUT_ERROR = "VSUM_INPUT_ERROR";

  /** An uploaded metamodel cannot be loaded or is not a valid Ecore model. */
  public static final String METAMODEL_ERROR = "VSUM_METAMODEL_ERROR";

  /** An uploaded reactions file does not fit the uploaded metamodels. */
  public static final String REACTIONS_ERROR = "VSUM_REACTIONS_ERROR";

  /**
   * The Maven build rejected the uploaded files: the metamodels, reactions, or constraints do not
   * compile, or the smoke test failed.
   */
  public static final String BUILD_FAILED = "VSUM_BUILD_FAILED";

  /**
   * The build could not run because of the build environment, e.g. a dependency could not be
   * resolved or the disk is full. Not caused by the uploaded files; retrying later may succeed.
   */
  public static final String BUILD_UNAVAILABLE = "VSUM_BUILD_UNAVAILABLE";

  /**
   * Code or build files that the service generates independently of the uploaded files do not
   * build, or the configured library versions are incompatible. A problem of the setup service or
   * its configuration.
   */
  public static final String INTERNAL_ERROR = "VSUM_INTERNAL_ERROR";

  /** The Maven build did not finish within the configured timeout. */
  public static final String BUILD_TIMEOUT = "VSUM_BUILD_TIMEOUT";

  /** An infrastructure problem (file system, process start, interruption) prevented the build. */
  public static final String BUILD_ERROR = "VSUM_BUILD_ERROR";

  private VsumErrorCodes() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }
}
