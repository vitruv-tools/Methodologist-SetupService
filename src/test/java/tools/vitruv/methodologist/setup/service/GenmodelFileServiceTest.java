package tools.vitruv.methodologist.setup.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.setup.exception.GenmodelException;
import tools.vitruv.methodologist.setup.model.service.GenmodelFileService;

@DisplayName("GenmodelFileService Tests")
class GenmodelFileServiceTest {

  private GenmodelFileService service;

  @TempDir
  File tempDir;

  @BeforeEach
  void setUp() {
    service = new GenmodelFileService();
  }

  @Test
  @DisplayName("Should save multipart file to temp location")
  void testSaveTempFile() {
    byte[] content = "<genmodel/>".getBytes();
    MultipartFile file = new MockMultipartFile(
        "file",
        "test.genmodel",
        "application/octet-stream",
        content
    );

    File tempFile = service.saveTempFile(file);

    assertNotNull(tempFile);
    assertTrue(tempFile.exists());
    assertTrue(tempFile.getName().contains("genmodel_"));
    assertTrue(tempFile.getName().endsWith(".genmodel"));

    tempFile.delete();
  }

  @Test
  @DisplayName("Should throw exception when file is empty")
  void testSaveTempFileEmpty() {
    MultipartFile file = new MockMultipartFile(
        "file",
        "empty.genmodel",
        "application/octet-stream",
        new byte[0]
    );

    GenmodelException exception = assertThrows(GenmodelException.class,
        () -> service.saveTempFile(file));

    assertEquals("EMPTY_FILE", exception.getErrorCode());
  }

  @Test
  @DisplayName("Should delete temporary file")
  void testDeleteTempFile() {
    byte[] content = "<genmodel/>".getBytes();
    MultipartFile file = new MockMultipartFile(
        "file",
        "test.genmodel",
        "application/octet-stream",
        content
    );

    File tempFile = service.saveTempFile(file);
    assertTrue(tempFile.exists());

    service.deleteTempFile(tempFile);
    assertFalse(tempFile.exists());
  }

  @Test
  @DisplayName("Should handle null file gracefully when deleting")
  void testDeleteNullFile() {
    assertDoesNotThrow(() -> service.deleteTempFile(null));
  }

  @Test
  @DisplayName("Should handle non-existent file gracefully when deleting")
  void testDeleteNonExistentFile() {
    File nonExistent = new File("/tmp/nonexistent_file_12345_xyz.genmodel");
    assertDoesNotThrow(() -> service.deleteTempFile(nonExistent));
  }

  @Test
  @DisplayName("Should preserve original filename in temp file")
  void testPreserveFilename() {
    byte[] content = "<genmodel/>".getBytes();
    MultipartFile file = new MockMultipartFile(
        "file",
        "mymodel.genmodel",
        "application/octet-stream",
        content
    );

    File tempFile = service.saveTempFile(file);

    assertTrue(tempFile.getName().contains("genmodel"));
    assertTrue(tempFile.getName().endsWith(".genmodel"));

    tempFile.delete();
  }

  @Test
  @DisplayName("Should convert multipart file to byte array")
  void testMultipartToBytes() {
    byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
    MultipartFile file = new MockMultipartFile(
        "file",
        "test.genmodel",
        "application/octet-stream",
        content
    );

    byte[] result = service.multipartToBytes(file);

    assertNotNull(result);
    assertArrayEquals(content, result);
  }

  @Test
  @DisplayName("Should throw exception converting empty multipart file to bytes")
  void testMultipartToBytesEmpty() {
    MultipartFile file = new MockMultipartFile(
        "file",
        "empty.genmodel",
        "application/octet-stream",
        new byte[0]
    );

    GenmodelException exception = assertThrows(GenmodelException.class,
        () -> service.multipartToBytes(file));

    assertEquals("EMPTY_FILE", exception.getErrorCode());
  }

  @Test
  @DisplayName("Should read file bytes successfully")
  void testReadFileBytes() throws Exception {
    byte[] content = "test file content".getBytes(StandardCharsets.UTF_8);
    File testFile = new File(tempDir, "test.genmodel");
    Files.write(testFile.toPath(), content);

    byte[] result = service.readFileBytes(testFile);

    assertNotNull(result);
    assertArrayEquals(content, result);
  }

  @Test
  @DisplayName("Should throw exception reading non-existent file")
  void testReadFileBytesNonExistent() {
    File nonExistent = new File(tempDir, "nonexistent.genmodel");

    GenmodelException exception = assertThrows(GenmodelException.class,
        () -> service.readFileBytes(nonExistent));

    assertEquals("FILE_READ_ERROR", exception.getErrorCode());
  }

  @Test
  @DisplayName("Should write bytes to file successfully")
  void testWriteFileBytes() throws Exception {
    byte[] content = "written content".getBytes(StandardCharsets.UTF_8);
    File testFile = new File(tempDir, "output.genmodel");

    service.writeFileBytes(testFile, content);

    assertTrue(testFile.exists());
    byte[] readBack = Files.readAllBytes(testFile.toPath());
    assertArrayEquals(content, readBack);
  }

  @Test
  @DisplayName("Should convert bytes to string with UTF-8 encoding")
  void testBytesToString() {
    String original = "test string üöä";
    byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

    String result = service.bytesToString(bytes);

    assertEquals(original, result);
  }

  @Test
  @DisplayName("Should convert string to bytes with UTF-8 encoding")
  void testStringToBytes() {
    String original = "test string üöä";

    byte[] result = service.stringToBytes(original);

    assertEquals(original, new String(result, StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("Should handle round-trip byte conversion")
  void testRoundTripConversion() {
    String original = "<?xml version=\"1.0\"?><genmodel/>";

    byte[] bytes = service.stringToBytes(original);
    String recovered = service.bytesToString(bytes);

    assertEquals(original, recovered);
  }
}

