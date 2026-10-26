package tools.vitruv.methodologist.setup.vsum.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Resolves reaction segment declarations to the class names emitted by the Reactions generator. */
final class ReactionSpecifications {
  // Mask comments and Xbase strings before reading declarations, including rich strings.
  private static final Pattern NON_CODE =
      Pattern.compile(
          "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
  private static final Pattern SEGMENT =
      Pattern.compile("\\breactions\\s*:\\s*\\^?([a-zA-Z_][a-zA-Z_0-9]*)");

  private ReactionSpecifications() {}

  /**
   * Reads all segments in upload order, without interpreting filenames as segment names.
   *
   * @param files reaction source files
   * @return distinct fully qualified specification class names
   * @throws IOException if a source file cannot be read
   */
  static List<String> read(List<Path> files) throws IOException {
    Set<String> specifications = new LinkedHashSet<>();
    for (Path file : files) {
      String code = NON_CODE.matcher(Files.readString(file)).replaceAll(" ");
      var segments = SEGMENT.matcher(code);
      while (segments.find()) {
        String name = segments.group(1);
        // Matches ClassNamesGenerators in tools.vitruv.dsls.reactions.language 3.1.0.
        String packageName = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        String className = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        specifications.add(
            "mir.reactions." + packageName + "." + className + "ChangePropagationSpecification");
      }
    }
    return List.copyOf(specifications);
  }
}
