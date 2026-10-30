package tools.vitruv.methodologist.setup.vsum.service.build;

import java.util.List;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

/**
 * A Maven build that ran and failed. Keeps the output so that the caller, which knows the uploaded
 * files, can attribute the errors to them.
 */
public class MavenBuildFailedException extends MethodologistSetupException {

  private static final long serialVersionUID = 1L;

  private final String header;
  private final transient List<String> output;
  private final String summary;

  /**
   * Creates the exception.
   *
   * @param errorCode the error code, which tells who is responsible
   * @param header what happened, e.g. "The VSUM build failed:"
   * @param output the complete Maven output
   * @param summary the relevant part of the output
   * @param status the HTTP status reported to the client
   */
  public MavenBuildFailedException(
      String errorCode, String header, List<String> output, String summary, HttpStatus status) {
    super(errorCode, header + "\n" + summary, status, null);
    this.header = header;
    this.output = List.copyOf(output);
    this.summary = summary;
  }

  /**
   * Returns what happened, without the Maven output.
   *
   * @return the header
   */
  public String getHeader() {
    return header;
  }

  /**
   * Returns the complete Maven output.
   *
   * @return the output lines
   */
  public List<String> getOutput() {
    return output;
  }

  /**
   * Returns the relevant part of the Maven output: its errors, or its tail.
   *
   * @return the summary
   */
  public String getSummary() {
    return summary;
  }
}
