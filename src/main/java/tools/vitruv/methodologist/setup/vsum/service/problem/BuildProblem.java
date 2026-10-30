package tools.vitruv.methodologist.setup.vsum.service.problem;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One problem of a VSUM build, attributed to the uploaded file that causes it.
 *
 * @param source the kind of input, or the party, responsible for the problem
 * @param file name of the uploaded file, {@code null} if the problem is not tied to one
 * @param line 1-based line in the uploaded file, {@code null} if unknown
 * @param column 1-based column in the line, {@code null} if unknown
 * @param message what is wrong
 * @param excerpt the affected line of the uploaded file, with a caret below the column if known
 * @param hint what to change, {@code null} if there is no specific advice
 * @param hiddenFollowUpErrors number of further errors that only follow from this one and are not
 *     reported, {@code null} if none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildProblem(
    Source source,
    String file,
    Integer line,
    Integer column,
    String message,
    String excerpt,
    String hint,
    Integer hiddenFollowUpErrors) {

  /** The kind of input, or the party, responsible for a problem. */
  public enum Source {
    /** An uploaded Ecore metamodel. */
    METAMODEL,
    /** An uploaded Reactions file. */
    REACTIONS,
    /** An uploaded VitruvOCL constraints file. */
    CONSTRAINTS,
    /** An uploaded view definition. */
    VIEWS,
    /** The build environment, e.g. dependency downloads; not the uploads. */
    INFRASTRUCTURE,
    /** The setup service or its configuration; not the uploads. */
    INTERNAL
  }

  /**
   * Creates a problem of a file without a known location.
   *
   * @param source the responsible kind of input
   * @param file the uploaded file, may be {@code null}
   * @param message what is wrong
   * @return the problem
   */
  public static BuildProblem of(Source source, String file, String message) {
    return new BuildProblem(source, file, null, null, message, null, null, null);
  }

  /**
   * Creates a problem at a location of an uploaded file, with an excerpt of that line.
   *
   * @param source the responsible kind of input
   * @param file the uploaded file
   * @param text the content of the uploaded file, used for the excerpt; may be {@code null}
   * @param line 1-based line, may be {@code null}
   * @param column 1-based column, may be {@code null}
   * @param message what is wrong
   * @return the problem
   */
  public static BuildProblem at(
      Source source, String file, String text, Integer line, Integer column, String message) {
    return new BuildProblem(
        source, file, line, column, message, Excerpts.of(text, line, column), null, null);
  }

  /**
   * Returns this problem with a hint.
   *
   * @param newHint what to change
   * @return the problem with the hint
   */
  public BuildProblem withHint(String newHint) {
    return new BuildProblem(
        source, file, line, column, message, excerpt, newHint, hiddenFollowUpErrors);
  }

  /**
   * Returns this problem with a different message.
   *
   * @param newMessage what is wrong
   * @return the problem with the message
   */
  public BuildProblem withMessage(String newMessage) {
    return new BuildProblem(
        source, file, line, column, newMessage, excerpt, hint, hiddenFollowUpErrors);
  }

  /**
   * Returns this problem with the number of follow-up errors it hides.
   *
   * @param count number of hidden follow-up errors; {@code 0} removes the count
   * @return the problem with the count
   */
  public BuildProblem withHiddenFollowUpErrors(int count) {
    return new BuildProblem(
        source, file, line, column, message, excerpt, hint, count == 0 ? null : count);
  }
}
