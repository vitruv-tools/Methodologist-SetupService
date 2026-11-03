package tools.vitruv.methodologist.setup.vsum.service.problem;

import java.util.List;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

/**
 * A failed VSUM build together with its problems, each attributed to the uploaded file that causes
 * it. The message is a readable rendering of the problems (see {@link ProblemReport}).
 */
public class VsumBuildException extends MethodologistSetupException {

  private static final long serialVersionUID = 1L;

  private final transient List<BuildProblem> problems;

  /**
   * Creates the exception with a message rendered from the problems.
   *
   * @param errorCode the error code
   * @param header first line of the message, e.g. "The VSUM build failed:"
   * @param problems the problems, root causes first
   * @param status the HTTP status reported to the client
   */
  public VsumBuildException(
      String errorCode, String header, List<BuildProblem> problems, HttpStatus status) {
    super(errorCode, ProblemReport.render(header, problems), status, null);
    this.problems = List.copyOf(problems);
  }

  /**
   * Creates a {@code 400 Bad Request} exception for uploads rejected before the build.
   *
   * @param errorCode the error code
   * @param header first line of the message
   * @param problems the problems
   */
  public VsumBuildException(String errorCode, String header, List<BuildProblem> problems) {
    this(errorCode, header, problems, HttpStatus.BAD_REQUEST);
  }

  /**
   * Returns the problems, root causes first.
   *
   * @return the problems
   */
  public List<BuildProblem> getProblems() {
    return problems;
  }
}
