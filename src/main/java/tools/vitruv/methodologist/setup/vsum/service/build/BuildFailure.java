package tools.vitruv.methodologist.setup.vsum.service.build;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Who is responsible for a failed Maven build, derived from its output.
 *
 * <p>Infrastructure failures are checked first, because a missing dependency or a full disk makes
 * later steps fail with errors that look like problems of the project.
 */
enum BuildFailure {

  /** The build environment failed, e.g. a dependency could not be resolved. Worth a retry. */
  INFRASTRUCTURE,

  /**
   * Like {@link #INFRASTRUCTURE}, but caused by a dependency missing from the local repository of
   * an offline build; an online build may succeed.
   */
  MISSING_OFFLINE,

  /**
   * Code or build files that the service generates independently of the uploads do not build, or
   * the configured library versions are incompatible with each other.
   */
  INTERNAL,

  /** The uploaded metamodels, reactions, or constraints do not build. */
  INPUT;

  private static final List<Pattern> MISSING_OFFLINE_PATTERNS =
      patterns("in offline mode", "has not been downloaded from it before");

  private static final List<Pattern> INFRASTRUCTURE_PATTERNS =
      patterns(
          "Could not resolve dependencies",
          "Could not transfer artifact",
          "Could not find artifact",
          "Failed to read artifact descriptor",
          "Failed to collect dependencies",
          "Non-resolvable parent POM",
          "Plugin \\S+ or one of its dependencies could not be resolved",
          "Unresolveable build extension",
          "Could not acquire (?:write |read )?lock",
          "OutOfMemoryError",
          "No space left on device",
          "Connection (?:reset|refused|timed out)",
          "UnknownHostException",
          "Could not create the Java Virtual Machine");

  // the vsum module and every POM are rendered from templates, independently of the uploads
  private static final List<Pattern> INTERNAL_PATTERNS =
      patterns(
          "on project " + Pattern.quote(ProjectLayout.VSUM_ARTIFACT_ID) + ": Compilation failure",
          "on project "
              + Pattern.quote(ProjectLayout.VSUM_ARTIFACT_ID)
              + ": Failed to create assembly",
          "Non-parseable POM",
          "Malformed POM",
          "The project \\S+ has \\d+ errors?",
          // linkage errors: the uploads are compiled against the same classpath, so these mean
          // that the configured versions of the libraries do not fit together
          "java\\.lang\\.(?:NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError"
              + "|AbstractMethodError|IncompatibleClassChangeError|UnsupportedClassVersionError)");

  /**
   * Classifies a failed build by its output.
   *
   * @param log the Maven output
   * @return the responsible party
   */
  static BuildFailure classify(List<String> log) {
    if (matchesAny(log, MISSING_OFFLINE_PATTERNS)) {
      return MISSING_OFFLINE;
    }
    if (matchesAny(log, INFRASTRUCTURE_PATTERNS)) {
      return INFRASTRUCTURE;
    }
    if (matchesAny(log, INTERNAL_PATTERNS)) {
      return INTERNAL;
    }
    return INPUT;
  }

  boolean isInfrastructure() {
    return this == INFRASTRUCTURE || this == MISSING_OFFLINE;
  }

  /**
   * Only errors count: Maven also logs warnings like "Could not transfer metadata" for problems it
   * recovered from. JVM errors and exceptions of failed tests appear without a log level.
   */
  private static boolean matchesAny(List<String> log, List<Pattern> patterns) {
    return log.stream()
        .filter(
            line ->
                line.startsWith("[ERROR]")
                    || line.startsWith("java.lang.")
                    || line.startsWith("Caused by: java.lang.")
                    || line.contains("OutOfMemoryError")
                    || line.contains("No space left on device"))
        .anyMatch(line -> patterns.stream().anyMatch(pattern -> pattern.matcher(line).find()));
  }

  private static List<Pattern> patterns(String... regexes) {
    return Arrays.stream(regexes).map(Pattern::compile).toList();
  }
}
