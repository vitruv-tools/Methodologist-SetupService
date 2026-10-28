package tools.vitruv.methodologist.setup.vsum.service.reactions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.emf.ecore.EcorePackage;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Checks uploaded Reactions files against the uploaded metamodels, places them into the generated
 * project, and determines the change propagation specifications the Reactions compiler generates.
 */
@Service
public class ReactionsProcessor {

  // Xbase string literals: rich strings, double- and single-quoted strings
  private static final Pattern STRINGS =
      Pattern.compile("'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
  private static final Pattern IMPORT = Pattern.compile("\\bimport\\s+\"([^\"]+)\"");
  private static final Pattern SEGMENT =
      Pattern.compile("\\breactions\\s*:\\s*\\^?([A-Za-z_][A-Za-z0-9_]*)");

  /**
   * Validates the reactions files, copies them into the consistency module, and returns the fully
   * qualified names of the {@code ChangePropagationSpecification} classes generated from them.
   *
   * @param reactions uploaded reactions files
   * @param metamodels the processed metamodels the reactions may import
   * @param projectRoot root of the generated project
   * @return generated change propagation specification class names, one per reactions segment
   * @throws MethodologistSetupException when a file declares no segment, a segment is declared
   *     twice, or a file imports a metamodel that was not uploaded
   * @throws IOException when writing the files fails
   */
  public List<String> process(
      List<UploadedFile> reactions, List<GeneratedMetamodel> metamodels, Path projectRoot)
      throws IOException {
    Set<String> knownNsUris = new LinkedHashSet<>();
    metamodels.forEach(
        metamodel -> metamodel.packages().forEach(pkg -> knownNsUris.add(pkg.nsUri())));
    metamodels.forEach(metamodel -> knownNsUris.add(metamodel.nsUri()));
    knownNsUris.add(EcorePackage.eNS_URI);

    List<String> errors = new ArrayList<>();
    Map<String, String> segmentOwners = new HashMap<>();
    List<String> specifications = new ArrayList<>();

    for (UploadedFile file : reactions) {
      String source = stripComments(file.text());

      Matcher imports = IMPORT.matcher(source);
      while (imports.find()) {
        String nsUri = imports.group(1);
        if (!knownNsUris.contains(nsUri)) {
          errors.add(
              String.format(
                  "%s: imports '%s', which is not the nsURI of an uploaded metamodel%s",
                  file.fileName(), nsUri, suggestion(nsUri, metamodels)));
        }
      }

      List<String> segments = new ArrayList<>();
      Matcher segmentMatcher = SEGMENT.matcher(STRINGS.matcher(source).replaceAll(" "));
      while (segmentMatcher.find()) {
        segments.add(segmentMatcher.group(1));
      }
      if (segments.isEmpty()) {
        errors.add(file.fileName() + ": declares no 'reactions: <name>' segment");
      }
      for (String segment : segments) {
        String owner = segmentOwners.putIfAbsent(segment.toLowerCase(), file.fileName());
        if (owner != null) {
          errors.add(
              String.format(
                  "%s: the reactions segment '%s' is already declared in %s",
                  file.fileName(), segment, owner));
        } else {
          specifications.add(changePropagationSpecification(segment));
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new MethodologistSetupException(
          VsumErrorCodes.REACTIONS_ERROR,
          "The uploaded reactions do not match the uploaded metamodels:\n- "
              + String.join("\n- ", errors));
    }

    Path directory = ProjectLayout.reactionsDirectory(projectRoot);
    Files.createDirectories(directory);
    for (UploadedFile file : reactions) {
      Files.write(directory.resolve(file.fileName()), file.content());
    }
    return specifications;
  }

  /**
   * Mirrors the class naming of the Reactions code generator ({@code ClassNamesGenerators}).
   *
   * @param segment the reactions segment name
   * @return fully qualified name of the generated change propagation specification
   */
  static String changePropagationSpecification(String segment) {
    String lower = Character.toLowerCase(segment.charAt(0)) + segment.substring(1);
    String upper = Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    return "mir.reactions." + lower + "." + upper + "ChangePropagationSpecification";
  }

  /**
   * Removes {@code //} and {@code /* *}{@code /} comments while leaving string literals (such as
   * {@code "http://..."} imports) untouched.
   *
   * @param source reactions source text
   * @return the source with comments replaced by spaces
   */
  static String stripComments(String source) {
    StringBuilder result = new StringBuilder(source.length());
    int index = 0;
    while (index < source.length()) {
      char current = source.charAt(index);
      int literalEnd = literalEnd(source, index);
      if (literalEnd > index) {
        result.append(source, index, literalEnd);
        index = literalEnd;
      } else if (source.startsWith("//", index)) {
        int end = source.indexOf('\n', index);
        index = end < 0 ? source.length() : end;
        result.append(' ');
      } else if (source.startsWith("/*", index)) {
        int end = source.indexOf("*/", index + 2);
        index = end < 0 ? source.length() : end + 2;
        result.append(' ');
      } else {
        result.append(current);
        index++;
      }
    }
    return result.toString();
  }

  /**
   * Returns the end of the string literal starting at {@code index}, or {@code index} if none
   * starts there. Recognizes Xtend rich strings and single- or double-quoted strings.
   */
  private static int literalEnd(String source, int index) {
    if (source.startsWith("'''", index)) {
      int end = source.indexOf("'''", index + 3);
      return end < 0 ? source.length() : end + 3;
    }
    char quote = source.charAt(index);
    if (quote != '"' && quote != '\'') {
      return index;
    }
    int end = index + 1;
    while (end < source.length() && source.charAt(end) != quote) {
      end += source.charAt(end) == '\\' ? 2 : 1;
    }
    return Math.min(end + 1, source.length());
  }

  private String suggestion(String nsUri, List<GeneratedMetamodel> metamodels) {
    String lastSegment = lastSegment(nsUri);
    return metamodels.stream()
        .filter(
            metamodel ->
                metamodel.name().equalsIgnoreCase(lastSegment)
                    || lastSegment(metamodel.nsUri()).equalsIgnoreCase(lastSegment))
        .findFirst()
        .map(metamodel -> " (did you mean '" + metamodel.nsUri() + "'?)")
        .orElse("");
  }

  private static String lastSegment(String uri) {
    String trimmed = uri.replaceAll("[/#]+$", "");
    return trimmed.substring(Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('#')) + 1);
  }
}
