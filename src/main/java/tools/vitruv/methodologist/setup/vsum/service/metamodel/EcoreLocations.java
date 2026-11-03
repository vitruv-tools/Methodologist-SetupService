package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
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

  /**
   * Names from the root package down to the element.
   *
   * @param element an element of a metamodel
   * @return e.g. {@code [model, Component, name]}
   */
  public static List<String> namePath(EObject element) {
    List<String> names = new ArrayList<>();
    for (EObject current = element; current != null; current = current.eContainer()) {
      if (current instanceof ENamedElement named && named.getName() != null) {
        names.add(0, named.getName());
      }
    }
    return names;
  }

  /**
   * Readable name of an element within its metamodel.
   *
   * @param element an element of a metamodel
   * @return e.g. {@code Component.name}; the package name only for the root package
   */
  public static String elementPath(EObject element) {
    List<String> names = namePath(element);
    return names.size() > 1
        ? String.join(".", names.subList(1, names.size()))
        : String.join(".", names);
  }
}
