package tools.vitruv.methodologist.setup.vsum.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

/**
 * An uploaded file held in memory, with a sanitized file name that is safe to use inside the
 * generated project.
 *
 * @param fileName sanitized file name without any directory components
 * @param content raw file content
 */
public record UploadedFile(String fileName, byte[] content) {

  private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]");

  /**
   * Validates the record components.
   *
   * @throws NullPointerException when a component is {@code null}
   */
  public UploadedFile {
    Objects.requireNonNull(fileName, "fileName must not be null");
    Objects.requireNonNull(content, "content must not be null");
  }

  /**
   * Reads a multipart upload into memory, checking that it is non-empty and carries the expected
   * file extension.
   *
   * @param file the multipart upload
   * @param label human-readable kind of file used in error messages, e.g. {@code "metamodel"}
   * @param extension required file extension including the dot, e.g. {@code ".ecore"}; {@code null}
   *     accepts any extension
   * @return the uploaded file
   * @throws MethodologistSetupException when the upload is empty, misnamed, or unreadable
   */
  public static UploadedFile fromMultipart(MultipartFile file, String label, String extension) {
    if (file == null || file.isEmpty()) {
      throw new MethodologistSetupException(
          VsumErrorCodes.INPUT_ERROR, "Uploaded " + label + " file must not be empty");
    }
    String fileName = sanitizeFileName(file.getOriginalFilename());
    if (fileName.isEmpty()) {
      throw new MethodologistSetupException(
          VsumErrorCodes.INPUT_ERROR, "Uploaded " + label + " file has no file name");
    }
    if (extension != null && !fileName.toLowerCase().endsWith(extension)) {
      throw new MethodologistSetupException(
          VsumErrorCodes.INPUT_ERROR,
          String.format(
              "Uploaded %s file '%s' must have the extension '%s'", label, fileName, extension));
    }
    try {
      return new UploadedFile(fileName, file.getBytes());
    } catch (IOException e) {
      throw new MethodologistSetupException(
          VsumErrorCodes.INPUT_ERROR,
          "Could not read uploaded " + label + " file '" + fileName + "'",
          e);
    }
  }

  /**
   * Strips directory components from a client-supplied file name and replaces characters that are
   * not safe in file names or build tooling.
   *
   * @param originalName the client-supplied name, may be {@code null}
   * @return the sanitized name, empty when nothing usable remains
   */
  static String sanitizeFileName(String originalName) {
    if (originalName == null) {
      return "";
    }
    String normalized = originalName.replace('\\', '/');
    String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    return UNSAFE_CHARACTERS.matcher(baseName).replaceAll("_");
  }

  /**
   * Returns the file name without its extension.
   *
   * @return the base name
   */
  public String baseName() {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  /**
   * Decodes the content as UTF-8 text.
   *
   * @return the textual content
   */
  public String text() {
    return new String(content, StandardCharsets.UTF_8);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof UploadedFile that
        && fileName.equals(that.fileName)
        && Arrays.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return 31 * fileName.hashCode() + Arrays.hashCode(content);
  }

  @Override
  public String toString() {
    return "UploadedFile[" + fileName + ", " + content.length + " bytes]";
  }
}
