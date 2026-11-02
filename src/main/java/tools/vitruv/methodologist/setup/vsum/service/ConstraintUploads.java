package tools.vitruv.methodologist.setup.vsum.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

/** Stores and checks uploaded OCL constraint files of a VSUM build. */
final class ConstraintUploads {
  private static final String VSUM_INPUT_ERROR_CODE = "VSUM_INPUT_ERROR";
  private static final String EXTENSION = ".ocl";
  private static final String DEFAULT_NAME = "constraints";

  /**
   * Maximum number of characters of a file name without extension, well below the limits of file
   * systems and of paths within the generated project.
   */
  private static final int MAX_NAME_LENGTH = 100;

  /**
   * Characters that are not allowed in file names on Windows, Linux or macOS, and invisible
   * formatting characters, which would make names misleading.
   */
  private static final Pattern INVALID_FILE_NAME_CHARACTERS =
      Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]", Pattern.UNICODE_CHARACTER_CLASS);

  private static final Pattern RESERVED_WINDOWS_NAMES =
      Pattern.compile("(?i)(con|prn|aux|nul|com\\d|lpt\\d)(\\..*)?");

  /**
   * Matches a context declaration, capturing the metamodel of a qualified context type such as
   * {@code context model::Component} or {@code context c : model::Component}.
   */
  private static final Pattern CONTEXT_PATTERN =
      Pattern.compile("\\bcontext\\s+(?:\\w+\\s*:\\s*)?(\\w+)(\\s*::)?");

  private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
  private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\r\\n]*");

  private ConstraintUploads() {}

  /**
   * Validates the uploaded constraint files and writes them into the given directory. Each file
   * gets a distinct name ending in {@code .ocl}. Parts without a file, as sent by forms without a
   * selected file, are ignored.
   *
   * @param directory the directory to write the files to
   * @param constraintFiles the uploaded files, may be {@code null}
   * @return the written files in upload order, empty if there are none
   * @throws IOException when writing a file fails
   * @throws MethodologistSetupException when a file is empty
   */
  static List<File> write(Path directory, List<MultipartFile> constraintFiles) throws IOException {
    if (constraintFiles == null || constraintFiles.isEmpty()) {
      return List.of();
    }
    Set<String> usedNames = new HashSet<>();
    List<File> files = new ArrayList<>();
    for (int index = 0; index < constraintFiles.size(); index++) {
      MultipartFile constraintFile = constraintFiles.get(index);
      if (constraintFile == null || isMissingFile(constraintFile)) {
        continue;
      }
      if (constraintFile.isEmpty()) {
        throw new MethodologistSetupException(
            VSUM_INPUT_ERROR_CODE,
            "Uploaded constraint file " + constraintFile.getOriginalFilename() + " is empty");
      }
      String name = uniqueName(constraintFile.getOriginalFilename(), index, usedNames);
      Path target = Files.createDirectories(directory).resolve(name);
      constraintFile.transferTo(target);
      files.add(target.toFile());
    }
    return files;
  }

  /**
   * Checks that every context declaration names its metamodel and that the metamodel is one of the
   * given metamodels, so that invalid constraints fail fast with a clear message instead of failing
   * the VSUM at runtime. Other errors are found by compiling the constraints, see {@link
   * ConstraintTypeCheck}.
   *
   * @param constraintFiles the constraint files
   * @param metamodelNames the package names of the uploaded metamodels
   * @throws IOException when reading a file fails
   * @throws MethodologistSetupException when a context is unqualified or refers to an unknown
   *     metamodel
   */
  static void checkMetamodels(List<File> constraintFiles, Collection<String> metamodelNames)
      throws IOException {
    for (File constraintFile : constraintFiles) {
      String content = withoutCommentsAndStrings(Files.readString(constraintFile.toPath()));
      Set<String> unknown = new TreeSet<>();
      Set<String> unqualified = new TreeSet<>();
      Matcher matcher = CONTEXT_PATTERN.matcher(content);
      while (matcher.find()) {
        if (matcher.group(2) == null) {
          unqualified.add(matcher.group(1));
        } else if (!metamodelNames.contains(matcher.group(1))) {
          unknown.add(matcher.group(1));
        }
      }
      if (!unqualified.isEmpty()) {
        throw new MethodologistSetupException(
            VSUM_INPUT_ERROR_CODE,
            String.format(
                "Constraint file %s declares context(s) %s without metamodel; use"
                    + " <metamodel>::<class>",
                constraintFile.getName(), unqualified));
      }
      if (!unknown.isEmpty()) {
        throw new MethodologistSetupException(
            VSUM_INPUT_ERROR_CODE,
            String.format(
                "Constraint file %s refers to unknown metamodel(s) %s; available metamodels: %s",
                constraintFile.getName(), unknown, new TreeSet<>(metamodelNames)));
      }
    }
  }

  private static boolean isMissingFile(MultipartFile file) {
    String name = file.getOriginalFilename();
    return file.isEmpty() && (name == null || name.isBlank());
  }

  private static String withoutCommentsAndStrings(String content) {
    String withoutStrings = STRING_LITERAL.matcher(content).replaceAll("\"\"");
    String withoutBlockComments = BLOCK_COMMENT.matcher(withoutStrings).replaceAll(" ");
    return LINE_COMMENT.matcher(withoutBlockComments).replaceAll("");
  }

  /**
   * Derives a file name from the uploaded name, which keeps the name, e.g. of a rule set, as far as
   * possible: only the directory part and characters that are invalid in file names are removed.
   * The result differs case-insensitively from all names used so far.
   */
  private static String uniqueName(String originalName, int index, Set<String> usedNames) {
    String baseName = originalName == null ? "" : originalName;
    if (baseName.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
      baseName = baseName.substring(0, baseName.length() - EXTENSION.length());
    }
    // path separators are replaced as well, so "Prices/Discounts" stays one recognizable name
    baseName = INVALID_FILE_NAME_CHARACTERS.matcher(baseName).replaceAll("_");
    baseName =
        baseName
            .codePoints()
            .limit(MAX_NAME_LENGTH)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    // leading dots would hide the file, trailing dots and spaces are dropped by Windows
    baseName = baseName.replaceAll("^[.\\s]+|[.\\s]+$", "");
    if (baseName.isEmpty() || baseName.matches("_+")) {
      baseName = DEFAULT_NAME;
    }
    if (RESERVED_WINDOWS_NAMES.matcher(baseName).matches()) {
      baseName = "_" + baseName;
    }
    String name = baseName + EXTENSION;
    for (int suffix = index; !usedNames.add(name.toLowerCase(Locale.ROOT)); suffix++) {
      name = baseName + "-" + suffix + EXTENSION;
    }
    return name;
  }
}
