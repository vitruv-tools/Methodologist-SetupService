package tools.vitruv.methodologist.setup.vsum.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads the VSUM test fixtures from {@code src/test/resources/vsum}. */
public final class TestFiles {

  private TestFiles() {}

  /**
   * Loads a fixture as an uploaded file.
   *
   * @param name fixture file name
   * @return the uploaded file
   */
  public static UploadedFile fixture(String name) {
    try (InputStream input = TestFiles.class.getResourceAsStream("/vsum/" + name)) {
      if (input == null) {
        throw new IllegalArgumentException("Unknown fixture " + name);
      }
      return new UploadedFile(name, input.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates an uploaded file from text.
   *
   * @param name file name
   * @param content file content
   * @return the uploaded file
   */
  public static UploadedFile text(String name, String content) {
    return new UploadedFile(name, content.getBytes(StandardCharsets.UTF_8));
  }
}
