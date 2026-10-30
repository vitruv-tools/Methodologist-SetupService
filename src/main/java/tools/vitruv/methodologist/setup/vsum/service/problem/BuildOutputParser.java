package tools.vitruv.methodologist.setup.vsum.service.problem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.build.MavenBuildFailedException;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;
import tools.vitruv.methodologist.setup.vsum.service.problem.ProjectSources.SourceFile;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Turns the output of a failed Maven build into problems attributed to the uploaded files.
 *
 * <p>Recognizes Reactions compiler (Xtext) errors, javac errors in code generated from the
 * metamodels or reactions, and failures of the generated smoke test; anything else is reported for
 * the module that failed. Errors that only follow from another one are hidden (see {@link
 * FollowUpErrors}).
 */
@Slf4j
public final class BuildOutputParser {

  private static final Pattern XTEXT_ERROR =
      Pattern.compile(
          "^\\[ERROR] ERROR:(.*) \\((?:file:)?(\\S.*?) line : (\\d+) column : (\\d+)\\)\\s*$");
  private static final Pattern JAVAC_ERROR =
      Pattern.compile("^\\[ERROR] (\\S+\\.java):\\[(\\d+),(\\d+)] (.*)$");
  private static final Pattern FAILED_MODULE =
      Pattern.compile("^\\[ERROR] Failed to execute goal .* on project (\\S+?):");
  private static final Pattern GENERATED_REACTIONS_CODE =
      Pattern.compile("/mir/(?:reactions|routines)/([^/]+)/");
  private static final Pattern REACTIONS_FRAME =
      Pattern.compile("\\bat mir\\.(reactions|routines)\\.([\\w$]+)\\.([\\w$]+)");
  private static final Pattern MODEL_FRAME =
      Pattern.compile(
          "\\bat ("
              + Pattern.quote(ProjectLayout.MODEL_BASE_PACKAGE)
              + "\\.[\\w.$]+)\\.[\\w$]+\\(");
  private static final Pattern CONSTRAINTS_MESSAGE =
      Pattern.compile("Constraints in (\\S+) cannot be evaluated:\\n", Pattern.DOTALL);
  private static final Pattern INVARIANT = Pattern.compile("\\binv\\s+(\\w+)");
  private static final Pattern CONTEXT = Pattern.compile("\\bcontext\\s+([\\w:]+)");

  private static final int MAX_CONTINUATION_LINES = 4;

  private BuildOutputParser() {}

  /**
   * Attributes the errors of a failed build to the uploads.
   *
   * @param failure the failed build
   * @param sources where the uploads are in the project
   * @return the problems, root causes first; never empty
   */
  public static List<BuildProblem> parse(
      MavenBuildFailedException failure, ProjectSources sources) {
    String summary = sources.sanitize(failure.getSummary());
    if (VsumErrorCodes.BUILD_UNAVAILABLE.equals(failure.getErrorCode())) {
      return List.of(BuildProblem.of(Source.INFRASTRUCTURE, null, summary));
    }
    if (VsumErrorCodes.INTERNAL_ERROR.equals(failure.getErrorCode())) {
      return List.of(BuildProblem.of(Source.INTERNAL, null, summary));
    }

    List<BuildProblem> problems = new ArrayList<>();
    List<String> output = failure.getOutput();
    for (int i = 0; i < output.size(); i++) {
      String line = output.get(i);
      Matcher xtext = XTEXT_ERROR.matcher(line);
      Matcher javac = JAVAC_ERROR.matcher(line);
      if (xtext.matches()) {
        problems.add(xtextProblem(xtext, sources));
      } else if (javac.matches()) {
        problems.add(javacProblem(javac, continuation(output, i), sources));
      }
    }
    problems.addAll(smokeTestProblems(sources));

    if (problems.isEmpty()) {
      problems.add(BuildProblem.of(failedModuleSource(output), null, summary));
    }
    return FollowUpErrors.hide(dedupe(problems)).stream()
        .sorted(Comparator.comparingInt(problem -> problem.source().ordinal()))
        .toList();
  }

  private static BuildProblem xtextProblem(Matcher xtext, ProjectSources sources) {
    String message = sources.sanitize(xtext.group(1).trim());
    int line = Integer.parseInt(xtext.group(3));
    int column = Integer.parseInt(xtext.group(4));
    Optional<SourceFile> file = sources.fileAt(Path.of(xtext.group(2)));
    if (file.isEmpty()) {
      return BuildProblem.of(Source.REACTIONS, null, message);
    }
    SourceFile source = file.get();
    return BuildProblem.at(
        source.source(),
        source.fileName(),
        source.text(),
        source.uploadedLine(line),
        column,
        message);
  }

  /** Lines following a javac error that belong to it, e.g. "symbol: ..." and "location: ...". */
  private static String continuation(List<String> output, int index) {
    StringBuilder text = new StringBuilder();
    for (int i = index + 1; i < output.size() && i <= index + MAX_CONTINUATION_LINES; i++) {
      String next = output.get(i);
      if (next.startsWith("[") || next.isBlank()) {
        break;
      }
      text.append('\n').append(next.strip());
    }
    return text.toString();
  }

  private static BuildProblem javacProblem(
      Matcher javac, String continuation, ProjectSources sources) {
    Path path = Path.of(javac.group(1)).toAbsolutePath().normalize();
    String message = sources.sanitize(javac.group(4).trim() + continuation);
    Path relative =
        path.startsWith(sources.projectRoot()) ? sources.projectRoot().relativize(path) : path;
    String module = relative.getNameCount() > 0 ? relative.getName(0).toString() : "";
    String unixPath = "/" + relative.toString().replace('\\', '/');

    if (module.equals(ProjectLayout.MODEL_MODULE)) {
      return metamodelCodeProblem(relative, message, sources);
    }
    if (module.equals(ProjectLayout.CONSISTENCY_MODULE)) {
      Matcher segment = GENERATED_REACTIONS_CODE.matcher(unixPath);
      Optional<SourceFile> reactions =
          segment.find() ? sources.reactionsOf(segment.group(1)) : Optional.empty();
      return BuildProblem.of(
              Source.REACTIONS,
              reactions.map(SourceFile::fileName).orElse(null),
              "the Java code generated from the reactions does not compile: " + message)
          .withHint(
              "Check the Java expressions in the reactions' update and match blocks, e.g. the"
                  + " types of the values that are assigned or compared");
    }
    return BuildProblem.of(
        Source.INTERNAL, null, "generated code does not compile: " + relative + ": " + message);
  }

  private static BuildProblem metamodelCodeProblem(
      Path relative, String message, ProjectSources sources) {
    String className = relative.getFileName().toString().replaceFirst("\\.java$", "");
    Path directory = relative.getParent();
    String javaPackage = javaPackage(directory);
    Optional<SourceFile> metamodel = sources.metamodelOf(javaPackage);
    String classifier = classifierName(className, directory);
    String text = "the Java code generated for '" + classifier + "' does not compile: " + message;
    if (metamodel.isEmpty()) {
      return BuildProblem.of(Source.METAMODEL, null, text);
    }
    SourceFile file = metamodel.get();
    Integer line =
        tools.vitruv.methodologist.setup.vsum.service.metamodel.EcoreLocations.lineOf(
            file.text(), List.of(classifier));
    return BuildProblem.at(Source.METAMODEL, file.fileName(), file.text(), line, null, text)
        .withHint(
            "Check the names in '"
                + classifier
                + "': they must be valid Java names that do not clash with generated methods"
                + " such as getClass() or eClass()");
  }

  /** The Java package of a generated source directory, e.g. {@code .../src-gen/a/b/impl}. */
  private static String javaPackage(Path directory) {
    if (directory == null) {
      return "";
    }
    String dotted = directory.toString().replace('\\', '/').replace('/', '.');
    int start = dotted.indexOf(ProjectLayout.MODEL_BASE_PACKAGE);
    return start < 0 ? dotted : dotted.substring(start);
  }

  /** The classifier a generated class belongs to, e.g. {@code ComponentImpl} → Component. */
  private static String classifierName(String className, Path directory) {
    String folder = directory == null ? "" : directory.getFileName().toString();
    if (folder.equals("impl") && className.endsWith("Impl")) {
      return className.substring(0, className.length() - "Impl".length());
    }
    return className;
  }

  private static List<BuildProblem> smokeTestProblems(ProjectSources sources) {
    Path reports =
        ProjectLayout.vsumModule(sources.projectRoot()).resolve("target/surefire-reports");
    if (!Files.isDirectory(reports)) {
      return List.of();
    }
    List<BuildProblem> problems = new ArrayList<>();
    try (Stream<Path> files = Files.list(reports)) {
      for (Path report :
          files
              .filter(path -> path.getFileName().toString().matches("TEST-.*\\.xml"))
              .sorted()
              .toList()) {
        problems.addAll(smokeTestProblems(report, sources));
      }
    } catch (IOException e) {
      log.warn("Could not read the smoke test reports in {}: {}", reports, e.getMessage());
    }
    return problems;
  }

  private static List<BuildProblem> smokeTestProblems(Path report, ProjectSources sources) {
    List<BuildProblem> problems = new ArrayList<>();
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      NodeList testCases =
          factory.newDocumentBuilder().parse(report.toFile()).getElementsByTagName("testcase");
      for (int i = 0; i < testCases.getLength(); i++) {
        Element testCase = (Element) testCases.item(i);
        for (String kind : List.of("failure", "error")) {
          NodeList failures = testCase.getElementsByTagName(kind);
          for (int j = 0; j < failures.getLength(); j++) {
            Element failure = (Element) failures.item(j);
            problems.addAll(
                smokeTestProblems(
                    failure.getAttribute("type"),
                    failure.getAttribute("message"),
                    failure.getTextContent(),
                    sources));
          }
        }
      }
    } catch (Exception e) {
      log.warn("Could not parse the smoke test report {}: {}", report, e.getMessage());
    }
    return problems;
  }

  /**
   * Attributes a failure of the smoke test: constraints that cannot be evaluated, or an exception
   * while creating the VSUM, located by the generated reactions or metamodel code on its stack.
   */
  static List<BuildProblem> smokeTestProblems(
      String type, String message, String stackTrace, ProjectSources sources) {
    Matcher constraints = CONSTRAINTS_MESSAGE.matcher(message);
    if (constraints.find()) {
      return constraintProblems(
          constraints.group(1), message.substring(constraints.end()), sources);
    }

    String exception = shortName(type) + (message.isBlank() ? "" : ": " + message.strip());
    Matcher reactionsFrame = REACTIONS_FRAME.matcher(stackTrace);
    if (reactionsFrame.find()) {
      Optional<SourceFile> file = sources.reactionsOf(reactionsFrame.group(2));
      String className = reactionsFrame.group(3);
      String kind = reactionsFrame.group(1).equals("routines") ? "routine" : "reaction";
      String name = elementName(className, kind);
      String text = "creating the VSUM failed in " + kind + " '" + name + "': " + exception;
      if (file.isPresent()) {
        Integer line = declarationLine(file.get().text(), kind, name);
        return List.of(
            BuildProblem.at(
                Source.REACTIONS, file.get().fileName(), file.get().text(), line, null, text));
      }
      return List.of(BuildProblem.of(Source.REACTIONS, null, text));
    }

    Matcher modelFrame = MODEL_FRAME.matcher(stackTrace);
    if (modelFrame.find()) {
      Optional<SourceFile> file = sources.metamodelOf(modelFrame.group(1));
      return List.of(
          BuildProblem.of(
              Source.METAMODEL,
              file.map(SourceFile::fileName).orElse(null),
              "creating the VSUM failed in the code generated from the metamodel: " + exception));
    }
    return List.of(
        BuildProblem.of(
            Source.INTERNAL, null, "the smoke test of the generated VSUM failed: " + exception));
  }

  private static List<BuildProblem> constraintProblems(
      String resource, String failures, ProjectSources sources) {
    Optional<SourceFile> file = sources.constraintsOf(resource);
    String fileName = file.map(SourceFile::fileName).orElse(null);
    List<BuildProblem> problems = new ArrayList<>();
    // "- <constraint>\n    <line>:<column> <error>\n..." per failed constraint
    for (String block : failures.split("(?m)^- ")) {
      if (block.isBlank()) {
        continue;
      }
      List<String> lines = block.lines().toList();
      String constraint = lines.get(0).strip();
      String errors =
          String.join(
              "; ",
              lines.subList(1, lines.size()).stream()
                  .map(String::strip)
                  .filter(error -> !error.isEmpty() && !error.startsWith("==>"))
                  .toList());
      String name = constraintName(constraint);
      String text =
          "the constraint '"
              + name
              + "' cannot be evaluated"
              + (errors.isEmpty() ? "" : ": " + errors);
      Integer line = file.map(source -> constraintLine(source.text(), constraint)).orElse(null);
      problems.add(
          BuildProblem.at(
              Source.CONSTRAINTS,
              fileName,
              file.map(SourceFile::text).orElse(null),
              line,
              null,
              text));
    }
    return problems;
  }

  private static String constraintName(String constraint) {
    Matcher invariant = INVARIANT.matcher(constraint);
    if (invariant.find()) {
      return invariant.group(1);
    }
    Matcher context = CONTEXT.matcher(constraint);
    return context.find() ? "context " + context.group(1) : constraint;
  }

  private static Integer constraintLine(String text, String constraint) {
    Matcher invariant = INVARIANT.matcher(constraint);
    Pattern declaration =
        invariant.find()
            ? Pattern.compile("\\binv\\s+" + Pattern.quote(invariant.group(1)) + "\\b")
            : null;
    if (declaration == null) {
      Matcher context = CONTEXT.matcher(constraint);
      if (!context.find()) {
        return null;
      }
      declaration = Pattern.compile("\\bcontext\\s+" + Pattern.quote(context.group(1)));
    }
    Matcher matcher = declaration.matcher(text);
    return matcher.find() ? Excerpts.lineOf(text, matcher.start()) : null;
  }

  /**
   * The declared name of a generated reaction or routine class, e.g. {@code
   * CreateAndRegisterRootRoutine$Update} → createAndRegisterRoot.
   */
  private static String elementName(String className, String kind) {
    int inner = className.indexOf('$');
    String outer = inner >= 0 ? className.substring(0, inner) : className;
    String suffix = Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
    String name =
        outer.endsWith(suffix) ? outer.substring(0, outer.length() - suffix.length()) : outer;
    if (name.isEmpty()) {
      return outer;
    }
    // routines are declared like methods, reactions like classes
    return kind.equals("routine")
        ? Character.toLowerCase(name.charAt(0)) + name.substring(1)
        : name;
  }

  /**
   * Line of {@code routine name} or {@code reaction Name}, ignoring the case of the first letter.
   */
  private static Integer declarationLine(String text, String kind, String name) {
    Matcher matcher =
        Pattern.compile(
                "\\b"
                    + kind
                    + "\\s+\\^?(?i:"
                    + Pattern.quote(name.substring(0, 1))
                    + ")"
                    + Pattern.quote(name.substring(1))
                    + "\\b")
            .matcher(text);
    return matcher.find() ? Excerpts.lineOf(text, matcher.start()) : null;
  }

  private static String shortName(String type) {
    return type == null || type.isBlank() ? "error" : type.substring(type.lastIndexOf('.') + 1);
  }

  private static Source failedModuleSource(List<String> output) {
    for (String line : output) {
      Matcher matcher = FAILED_MODULE.matcher(line);
      if (matcher.find()) {
        String module = matcher.group(1);
        if (module.equals(ProjectLayout.MODEL_ARTIFACT_ID)) {
          return Source.METAMODEL;
        }
        if (module.equals(ProjectLayout.CONSISTENCY_ARTIFACT_ID)) {
          return Source.REACTIONS;
        }
        return Source.INTERNAL;
      }
    }
    return Source.INTERNAL;
  }

  private static List<BuildProblem> dedupe(List<BuildProblem> problems) {
    List<BuildProblem> unique = new ArrayList<>();
    for (BuildProblem problem : problems) {
      if (!unique.contains(problem)) {
        unique.add(problem);
      }
    }
    return unique;
  }
}
