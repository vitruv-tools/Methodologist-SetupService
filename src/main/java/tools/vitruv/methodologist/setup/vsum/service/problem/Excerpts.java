package tools.vitruv.methodologist.setup.vsum.service.problem;

import java.util.List;

/** Cuts the affected line out of an uploaded file. */
public final class Excerpts {

  private Excerpts() {}

  /**
   * Returns the line of the text, followed by a caret line under the column if the column is known.
   *
   * @param text the file content, may be {@code null}
   * @param line 1-based line, may be {@code null}
   * @param column 1-based column, may be {@code null}
   * @return the excerpt, or {@code null} if the text or line is unknown or out of range
   */
  public static String of(String text, Integer line, Integer column) {
    if (text == null || line == null || line < 1) {
      return null;
    }
    List<String> lines = text.lines().toList();
    if (line > lines.size()) {
      return null;
    }
    String content = lines.get(line - 1).replace("\t", "    ");
    if (column == null || column < 1) {
      return content;
    }
    // tabs before the column are expanded like in the content, so the caret stays aligned
    String original = lines.get(line - 1);
    String prefix = original.substring(0, Math.min(column - 1, original.length()));
    int offset = prefix.replace("\t", "    ").length();
    return content + "\n" + " ".repeat(offset) + "^";
  }

  /**
   * Returns the 1-based line containing the character at the offset.
   *
   * @param text the file content
   * @param offset 0-based character offset
   * @return the line number
   */
  public static int lineOf(String text, int offset) {
    int line = 1;
    for (int i = 0; i < Math.min(offset, text.length()); i++) {
      if (text.charAt(i) == '\n') {
        line++;
      }
    }
    return line;
  }

  /**
   * Returns the 1-based column of the character at the offset.
   *
   * @param text the file content
   * @param offset 0-based character offset
   * @return the column number
   */
  public static int columnOf(String text, int offset) {
    int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1));
    return offset - lineStart;
  }
}
