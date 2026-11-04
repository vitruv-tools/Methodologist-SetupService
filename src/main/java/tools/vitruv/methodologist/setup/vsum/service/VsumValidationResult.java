package tools.vitruv.methodologist.setup.vsum.service;

import java.util.List;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;

/**
 * The result of checking a VSUM project without building it.
 *
 * @param valid whether the build would get past its checks, i.e. there is no error
 * @param problems the problems the build would report before running Maven, root causes first
 */
public record VsumValidationResult(boolean valid, List<BuildProblem> problems) {

  /** Makes the problem list immutable. */
  public VsumValidationResult {
    problems = List.copyOf(problems);
  }

  /**
   * Creates the result for the problems of the checks; the project is valid if there are none.
   *
   * @param problems the problems, empty if the checks passed
   * @return the result
   */
  public static VsumValidationResult of(List<BuildProblem> problems) {
    return new VsumValidationResult(problems.isEmpty(), problems);
  }
}
