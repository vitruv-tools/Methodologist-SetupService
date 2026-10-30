package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.vitruv.methodologist.setup.vsum.service.problem.Excerpts;

/**
 * Finds elements in the XMI text of an uploaded {@code .ecore} file.
 *
 * <p>EMF does not keep line numbers, so an element is located by the {@code name} attributes of its
 * containment path: the package, then the class within it, then the feature within that.
 */
public final class EcoreLocations {

  private EcoreLocations() {}

  /**
   * Returns the line of the element with the given containment path of names.
   *
   * @param text the content of the {@code .ecore} file
   * @param namePath names from the root package down to the element, e.g. {@code [model, Component,
   *     name]}
   * @return the 1-based line of the innermost name found, or {@code null} if none was found
   */
  public static Integer lineOf(String text, List<String> namePath) {
    int offset = 0;
    Integer line = null;
    for (String name : namePath) {
      Matcher matcher =
          Pattern.compile("\\bname\\s*=\\s*\"" + Pattern.quote(name) + "\"").matcher(text);
      if (!matcher.find(offset)) {
        break;
      }
      offset = matcher.end();
      line = Excerpts.lineOf(text, matcher.start());
    }
    return line;
  }
}
