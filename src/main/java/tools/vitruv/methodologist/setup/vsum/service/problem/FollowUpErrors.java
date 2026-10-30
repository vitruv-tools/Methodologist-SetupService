package tools.vitruv.methodologist.setup.vsum.service.problem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;

/**
 * Hides errors of the Reactions compiler that only follow from another error in the same file, so
 * that the root cause is not buried.
 *
 * <ul>
 *   <li>A syntax error makes the parser lose track, and most later errors of the file are caused by
 *       that. If a file has syntax errors, only those are reported.
 *   <li>One unknown name is reported at every use; it is reported once, with the other lines.
 *   <li>"missing type unknown" only follows from names that cannot be resolved.
 * </ul>
 */
final class FollowUpErrors {

  private static final Pattern SYNTAX_ERROR =
      Pattern.compile(
          "^(?:mismatched input|missing |extraneous input|no viable alternative"
              + "|mismatched character|required \\(\\.\\.\\.\\)\\+ loop)");
  private static final Pattern UNRESOLVED = Pattern.compile("^(\\S+) cannot be resolved\\.?$");
  private static final Pattern UNKNOWN_TYPE = Pattern.compile("missing type unknown");

  private FollowUpErrors() {}

  /**
   * Returns the problems without follow-up errors; the remaining problems count what they hide.
   *
   * @param problems all problems
   * @return the problems that are root causes, in their original order
   */
  static List<BuildProblem> hide(List<BuildProblem> problems) {
    Map<String, List<BuildProblem>> reactionsByFile = new LinkedHashMap<>();
    for (BuildProblem problem : problems) {
      if (problem.source() == Source.REACTIONS && problem.file() != null) {
        reactionsByFile.computeIfAbsent(problem.file(), file -> new ArrayList<>()).add(problem);
      }
    }
    Map<String, List<BuildProblem>> keptByFile = new LinkedHashMap<>();
    reactionsByFile.forEach((file, fileProblems) -> keptByFile.put(file, hideInFile(fileProblems)));

    List<BuildProblem> result = new ArrayList<>();
    for (BuildProblem problem : problems) {
      if (problem.source() == Source.REACTIONS && problem.file() != null) {
        List<BuildProblem> kept = keptByFile.remove(problem.file());
        if (kept != null) {
          result.addAll(kept);
        }
      } else {
        result.add(problem);
      }
    }
    return result;
  }

  private static List<BuildProblem> hideInFile(List<BuildProblem> problems) {
    List<BuildProblem> syntaxErrors =
        problems.stream()
            .filter(problem -> SYNTAX_ERROR.matcher(problem.message()).find())
            .toList();
    if (!syntaxErrors.isEmpty()) {
      return attachHidden(syntaxErrors, problems.size() - syntaxErrors.size());
    }

    List<BuildProblem> kept = new ArrayList<>();
    Map<String, Integer> unresolvedIndex = new LinkedHashMap<>();
    Map<String, List<Integer>> otherLines = new LinkedHashMap<>();
    int hidden = 0;
    for (BuildProblem problem : problems) {
      Matcher unresolved = UNRESOLVED.matcher(problem.message());
      if (unresolved.matches()) {
        String name = unresolved.group(1);
        Integer index = unresolvedIndex.get(name);
        if (index == null) {
          unresolvedIndex.put(name, kept.size());
          kept.add(problem.withMessage("'" + name + "' cannot be resolved"));
        } else {
          otherLines.computeIfAbsent(name, key -> new ArrayList<>()).add(problem.line());
        }
        continue;
      }
      if (UNKNOWN_TYPE.matcher(problem.message()).find() && problems.size() > 1) {
        hidden++;
        continue;
      }
      kept.add(problem);
    }
    for (Map.Entry<String, List<Integer>> entry : otherLines.entrySet()) {
      int index = unresolvedIndex.get(entry.getKey());
      BuildProblem first = kept.get(index);
      List<Integer> lines = entry.getValue().stream().filter(Objects::nonNull).toList();
      String also =
          lines.isEmpty()
              ? " (" + entry.getValue().size() + " more times)"
              : " (also in line" + (lines.size() == 1 ? " " : "s ") + join(lines) + ")";
      kept.set(index, first.withMessage(first.message() + also));
    }
    return attachHidden(kept, hidden);
  }

  private static List<BuildProblem> attachHidden(List<BuildProblem> kept, int hidden) {
    if (hidden == 0 || kept.isEmpty()) {
      return kept;
    }
    List<BuildProblem> result = new ArrayList<>(kept);
    result.set(0, kept.get(0).withHiddenFollowUpErrors(hidden));
    return result;
  }

  private static String join(List<Integer> lines) {
    return String.join(", ", lines.stream().map(String::valueOf).toList());
  }
}
