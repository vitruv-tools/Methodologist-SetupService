package tools.vitruv.methodologist.setup.vsum.service.problem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders problems as readable text, grouped by file in the order of their first problem:
 *
 * <pre>
 * The VSUM build failed:
 *
 * reaction-1.reactions:
 * - line 4, column 8: mismatched input 'java' expecting RULE_STRING
 *       import java.io.File
 *              ^
 *   (5 follow-up errors hidden)
 * </pre>
 */
public final class ProblemReport {

  private static final String INDENT = "      ";

  private ProblemReport() {}

  /**
   * Renders the problems below the header.
   *
   * @param header first line, e.g. "The VSUM build failed:"
   * @param problems the problems, root causes first
   * @return the text
   */
  public static String render(String header, List<BuildProblem> problems) {
    Map<String, List<BuildProblem>> byGroup = new LinkedHashMap<>();
    for (BuildProblem problem : problems) {
      byGroup.computeIfAbsent(group(problem), key -> new ArrayList<>()).add(problem);
    }
    StringBuilder text = new StringBuilder(header);
    byGroup.forEach(
        (group, groupProblems) -> {
          text.append("\n\n").append(group).append(':');
          groupProblems.forEach(problem -> append(text, problem));
        });
    return text.toString();
  }

  private static String group(BuildProblem problem) {
    if (problem.file() != null) {
      return problem.file();
    }
    return switch (problem.source()) {
      case INFRASTRUCTURE -> "Build environment";
      case INTERNAL -> "Setup service";
      case METAMODEL -> "Metamodels";
      case REACTIONS -> "Reactions";
      case CONSTRAINTS -> "Constraints";
      case VIEWS -> "Views";
    };
  }

  private static void append(StringBuilder text, BuildProblem problem) {
    text.append("\n- ");
    if (problem.line() != null) {
      text.append("line ").append(problem.line());
      if (problem.column() != null) {
        text.append(", column ").append(problem.column());
      }
      text.append(": ");
    }
    text.append(problem.message().replace("\n", "\n  "));
    if (problem.excerpt() != null) {
      text.append('\n').append(INDENT).append(problem.excerpt().replace("\n", "\n" + INDENT));
    }
    if (problem.hint() != null) {
      text.append("\n  Hint: ").append(problem.hint());
    }
    if (problem.hiddenFollowUpErrors() != null) {
      int hidden = problem.hiddenFollowUpErrors();
      text.append("\n  (")
          .append(hidden)
          .append(hidden == 1 ? " follow-up error" : " follow-up errors")
          .append(" hidden)");
    }
  }
}
