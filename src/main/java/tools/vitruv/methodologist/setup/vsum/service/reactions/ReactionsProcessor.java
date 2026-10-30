package tools.vitruv.methodologist.setup.vsum.service.reactions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.Excerpts;
import tools.vitruv.methodologist.setup.vsum.service.problem.VsumBuildException;
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
  // Xbase import: import [static [extension]] qualified.Name[.*][;]
  private static final Pattern JAVA_IMPORT =
      Pattern.compile(
          "\\s*import\\s+(?:static\\s+(?:extension\\s+)?)?\\^?[A-Za-z_$][\\w$]*"
              + "(?:\\s*\\.\\s*\\^?[A-Za-z_$][\\w$]*)*(?:\\s*\\.\\s*\\*)?\\s*;?\\s*(?://.*)?");
  private static final Pattern METAMODEL_IMPORT_START = Pattern.compile("\\s*import\\s+\"");
  private static final Pattern SEGMENT_START = Pattern.compile("\\s*reactions\\s*:");

  /**
   * Validates the reactions files, copies them into the consistency module, and returns the fully
   * qualified names of the {@code ChangePropagationSpecification} classes generated from them.
   *
   * @param reactions uploaded reactions files
   * @param metamodels the processed metamodels the reactions may import
   * @param projectRoot root of the generated project
   * @return generated change propagation specification class names, one per reactions segment
   * @throws VsumBuildException when a file declares no segment, a segment is declared twice, or a
   *     file imports a metamodel that was not uploaded
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

    List<BuildProblem> problems = new ArrayList<>();
    Map<String, String> segmentOwners = new HashMap<>();
    List<String> specifications = new ArrayList<>();

    for (UploadedFile file : reactions) {
      String text = file.text();
      String source = stripComments(text);

      Matcher imports = IMPORT.matcher(source);
      while (imports.find()) {
        String nsUri = imports.group(1);
        if (!knownNsUris.contains(nsUri)) {
          problems.add(
              problem(
                      file,
                      imports.start(1) - 1,
                      String.format(
                          "imports '%s', which is not the nsURI of an uploaded metamodel", nsUri))
                  .withHint(suggestion(nsUri, metamodels, knownNsUris)));
        }
      }

      List<Segment> segments = segments(source);
      if (segments.isEmpty()) {
        problems.add(
            BuildProblem.of(Source.REACTIONS, file.fileName(), "declares no reactions segment")
                .withHint(
                    "Start the reactions with a segment declaration like 'reactions: AToB',"
                        + " followed by 'in reaction to changes in a' and 'execute actions in b'"));
      }
      for (Segment segment : segments) {
        String owner = segmentOwners.putIfAbsent(segment.name().toLowerCase(), file.fileName());
        if (owner != null) {
          problems.add(
              problem(
                      file,
                      segment.offset(),
                      String.format(
                          "the reactions segment '%s' is already declared in %s",
                          segment.name(), owner))
                  .withHint(
                      "Segment names must be unique across all reactions files, ignoring case;"
                          + " rename one of them"));
        } else {
          specifications.add(changePropagationSpecification(segment.name()));
        }
      }
    }

    if (!problems.isEmpty()) {
      throw new VsumBuildException(
          VsumErrorCodes.REACTIONS_ERROR,
          "The uploaded reactions do not match the uploaded metamodels:",
          problems);
    }

    Path directory = ProjectLayout.reactionsDirectory(projectRoot);
    Files.createDirectories(directory);
    for (UploadedFile file : reactions) {
      String text = file.text();
      String normalized = hoistJavaImports(text);
      if (normalized.equals(text)) {
        Files.write(directory.resolve(file.fileName()), file.content());
      } else {
        Files.writeString(directory.resolve(file.fileName()), normalized, StandardCharsets.UTF_8);
      }
    }
    return specifications;
  }

  /**
   * Moves Java imports ({@code import java.io.File}) in front of the metamodel imports ({@code
   * import "nsUri" as alias}). The Reactions grammar only accepts them in that order, but the
   * editors start a file with the metamodel imports, so Java imports are usually added below them.
   * Files in the right order are returned unchanged.
   *
   * @param source reactions source text
   * @return the source with all Java imports of the header placed first
   */
  static String hoistJavaImports(String source) {
    return hoist(source).text();
  }

  /**
   * Maps the lines of the file as written into the project back to the lines of the upload, which
   * differ if Java imports were moved (see {@link #hoistJavaImports(String)}).
   *
   * @param source reactions source text as uploaded
   * @return for each 0-based line of the written file, the 1-based line of the upload
   */
  public static int[] uploadedLines(String source) {
    return hoist(source).uploadedLines();
  }

  private static Hoisted hoist(String source) {
    String[] lines = source.split("\n", -1);
    List<Integer> javaImports = new ArrayList<>();
    List<Integer> rest = new ArrayList<>();
    boolean metamodelImportSeen = false;
    boolean misplaced = false;
    boolean inBlockComment = false;
    int index = 0;
    for (; index < lines.length; index++) {
      String line = lines[index];
      if (inBlockComment) {
        inBlockComment = !line.contains("*/");
      } else if (SEGMENT_START.matcher(line).lookingAt()) {
        break;
      } else if (JAVA_IMPORT.matcher(line).matches()) {
        javaImports.add(index);
        misplaced |= metamodelImportSeen;
        continue;
      } else if (METAMODEL_IMPORT_START.matcher(line).lookingAt()) {
        metamodelImportSeen = true;
      } else if (line.contains("/*") && !line.contains("*/")) {
        inBlockComment = true;
      }
      rest.add(index);
    }
    List<Integer> order = new ArrayList<>();
    if (misplaced) {
      order.addAll(javaImports);
      order.addAll(rest);
    } else {
      for (int i = 0; i < index; i++) {
        order.add(i);
      }
    }
    for (int i = index; i < lines.length; i++) {
      order.add(i);
    }
    String text =
        misplaced ? String.join("\n", order.stream().map(i -> lines[i]).toList()) : source;
    return new Hoisted(text, order.stream().mapToInt(i -> i + 1).toArray());
  }

  private record Hoisted(String text, int[] uploadedLines) {}

  /**
   * Finds the reactions segments declared in a source without comments (see {@link
   * #stripComments(String)}); declarations inside string literals are ignored.
   *
   * @param source reactions source text without comments
   * @return the segments with the offset of their declaration
   */
  public static List<Segment> segments(String source) {
    List<Segment> segments = new ArrayList<>();
    Matcher matcher = SEGMENT.matcher(blankStrings(source));
    while (matcher.find()) {
      segments.add(new Segment(matcher.group(1), matcher.start()));
    }
    return segments;
  }

  /**
   * A declared reactions segment.
   *
   * @param name the segment name, without a leading {@code ^}
   * @param offset character offset of the {@code reactions} keyword
   */
  public record Segment(String name, int offset) {}

  /** Replaces string literals by spaces of the same length, keeping offsets and line breaks. */
  private static String blankStrings(String source) {
    Matcher matcher = STRINGS.matcher(source);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(matcher.group().replaceAll("[^\n]", " ")));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static BuildProblem problem(UploadedFile file, int offset, String message) {
    String text = file.text();
    return BuildProblem.at(
        Source.REACTIONS,
        file.fileName(),
        text,
        Excerpts.lineOf(text, offset),
        Excerpts.columnOf(text, offset),
        message);
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
   * {@code "http://..."} imports) untouched. Comments are replaced by spaces of the same length
   * with their line breaks kept, so offsets and lines stay those of the source.
   *
   * @param source reactions source text
   * @return the source with comments replaced by spaces
   */
  public static String stripComments(String source) {
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
        end = end < 0 ? source.length() : end;
        result.append(blank(source.substring(index, end)));
        index = end;
      } else if (source.startsWith("/*", index)) {
        int end = source.indexOf("*/", index + 2);
        end = end < 0 ? source.length() : end + 2;
        result.append(blank(source.substring(index, end)));
        index = end;
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
  private static String blank(String text) {
    return text.replaceAll("[^\n]", " ");
  }

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

  private String suggestion(
      String nsUri, List<GeneratedMetamodel> metamodels, Set<String> knownNsUris) {
    String lastSegment = lastSegment(nsUri);
    return metamodels.stream()
        .filter(
            metamodel ->
                metamodel.name().equalsIgnoreCase(lastSegment)
                    || lastSegment(metamodel.nsUri()).equalsIgnoreCase(lastSegment))
        .findFirst()
        .map(metamodel -> "Did you mean '" + metamodel.nsUri() + "'?")
        .orElse(
            "Import a metamodel by its nsURI; the uploaded metamodels have: "
                + String.join(
                    ", ",
                    knownNsUris.stream()
                        .filter(uri -> !uri.equals(EcorePackage.eNS_URI))
                        .toList()));
  }

  private static String lastSegment(String uri) {
    String trimmed = uri.replaceAll("[/#]+$", "");
    return trimmed.substring(Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('#')) + 1);
  }
}
