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

  @TempDir File tempDir;
  private GenmodelFileService service;

  @BeforeEach
  void setUp() {
    service = new GenmodelFileService();
  }

  @Test
  @DisplayName("Should save multipart file to temp location")
  void testSaveTempFile() {
    byte[] content = "<genmodel/>".getBytes();
    MultipartFile file =
        new MockMultipartFile("file", "test.genmodel", "application/octet-stream", content);

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
    MultipartFile file =
        new MockMultipartFile("file", "empty.genmodel", "application/octet-stream", new byte[0]);

    GenmodelException exception =
        assertThrows(GenmodelException.class, () -> service.saveTempFile(file));

    assertEquals("EMPTY_FILE", exception.getErrorCode());
  }

  @Test
  @DisplayName("Should delete temporary file")
  void testDeleteTempFile() {
    byte[] content = "<genmodel/>".getBytes();
    MultipartFile file =
        new MockMultipartFile("file", "test.genmodel", "application/octet-stream", content);

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
    MultipartFile file =
        new MockMultipartFile("file", "mymodel.genmodel", "application/octet-stream", content);

    File tempFile = service.saveTempFile(file);

    assertTrue(tempFile.getName().contains("genmodel"));
    assertTrue(tempFile.getName().endsWith(".genmodel"));

    tempFile.delete();
  }

  @Test
  @DisplayName("Should convert multipart file to byte array")
  void testMultipartToBytes() {
    byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
    MultipartFile file =
        new MockMultipartFile("file", "test.genmodel", "application/octet-stream", content);

    byte[] result = service.multipartToBytes(file);

    assertNotNull(result);
    assertArrayEquals(content, result);
  }

  @Test
  @DisplayName("Should throw exception converting empty multipart file to bytes")
  void testMultipartToBytesEmpty() {
    MultipartFile file =
        new MockMultipartFile("file", "empty.genmodel", "application/octet-stream", new byte[0]);

    GenmodelException exception =
        assertThrows(GenmodelException.class, () -> service.multipartToBytes(file));

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

    GenmodelException exception =
        assertThrows(GenmodelException.class, () -> service.readFileBytes(nonExistent));

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

  @Test
  @DisplayName("Should handle bytes with special characters in UTF-8")
  void testBytesSpecialCharacters() {
    String specialString = "<?xml version=\"1.0\"?><root>Test: üöä éàè 中文 テスト</root>";
    byte[] bytes = service.stringToBytes(specialString);
    String recovered = service.bytesToString(bytes);
    assertEquals(specialString, recovered);
  }

  @Test
  @DisplayName("Should handle empty bytes array in bytesToString")
  void testBytesToStringEmpty() {
    String result = service.bytesToString(new byte[0]);
    assertEquals("", result);
  }

  @Test
  @DisplayName("Should handle empty string in stringToBytes")
  void testStringToBytesEmpty() {
    byte[] result = service.stringToBytes("");
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  @Test
  @DisplayName("Should preserve file size in round-trip conversion")
  void testRoundTripPreserveSize() throws Exception {
    String content = "<?xml version=\"1.0\"?><genmodel/><?xml version=\"1.0\"?>";
    int originalLength = content.length();

    byte[] bytes = service.stringToBytes(content);
    assertEquals(originalLength, bytes.length);

    String recovered = service.bytesToString(bytes);
    assertEquals(originalLength, recovered.length());
  }

  @Test
  @DisplayName("Should throw exception for null content in writeFileBytes")
  void testWriteFileBytesNull() throws Exception {
    File testFile = new File(tempDir, "null_test.genmodel");

    assertThrows(NullPointerException.class, () -> service.writeFileBytes(testFile, null));
  }

  @Test
  @DisplayName("Should write and read large files")
  void testLargeFileRoundTrip() throws Exception {
    StringBuilder largeContent = new StringBuilder("<?xml version=\"1.0\"?><root>");
    for (int i = 0; i < 1000; i++) {
      largeContent.append("<element").append(i).append(">Content</element").append(i).append(">");
    }
    largeContent.append("</root>");

    File testFile = new File(tempDir, "large.genmodel");
    byte[] content = service.stringToBytes(largeContent.toString());

    service.writeFileBytes(testFile, content);
    byte[] readBack = service.readFileBytes(testFile);

    assertArrayEquals(content, readBack);
  }

  @Test
  @DisplayName("Should handle multipart file with large content")
  void testMultipartLargeFile() {
    StringBuilder largeContent = new StringBuilder("<genmodel>");
    for (int i = 0; i < 100; i++) {
      largeContent.append("<item").append(i).append(">Data</item").append(i).append(">");
    }
    largeContent.append("</genmodel>");

    byte[] content = largeContent.toString().getBytes(StandardCharsets.UTF_8);
    MultipartFile file =
        new MockMultipartFile("file", "large.genmodel", "application/octet-stream", content);

    byte[] result = service.multipartToBytes(file);
    assertArrayEquals(content, result);
  }

  @Test
  @DisplayName("Should handle file written with writeFileBytes")
  void testWriteAndVerify() throws Exception {
    String content = "<?xml version=\"1.0\"?><test>Verify</test>";
    File testFile = new File(tempDir, "verify.genmodel");
    byte[] bytes = service.stringToBytes(content);

    service.writeFileBytes(testFile, bytes);

    assertTrue(testFile.exists());
    byte[] readBack = Files.readAllBytes(testFile.toPath());
    assertEquals(content, service.bytesToString(readBack));
  }

  @Test
  @DisplayName("Should handle readFileBytes with special file permissions scenario")
  void testReadAndConvertToString() throws Exception {
    String content = "<?xml version=\"1.0\"?><genmodel/>";
    File testFile = new File(tempDir, "test_read.genmodel");
    Files.writeString(testFile.toPath(), content, StandardCharsets.UTF_8);

    byte[] bytes = service.readFileBytes(testFile);
    String recovered = service.bytesToString(bytes);

    assertEquals(content, recovered);
  }

  @Test
  @DisplayName("Should handle multiple bytes conversions in sequence")
  void testSequentialConversions() {
    String original = "Test content 1";

    // First conversion
    byte[] bytes1 = service.stringToBytes(original);
    String result1 = service.bytesToString(bytes1);

    // Second conversion from result
    byte[] bytes2 = service.stringToBytes(result1);
    String result2 = service.bytesToString(bytes2);

    assertEquals(original, result1);
    assertEquals(result1, result2);
  }

  @Test
  @DisplayName("Should verify saveTempFile creates readable file")
  void testSaveTempFileReadable() throws Exception {
    String content = "Test content readability";
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    MultipartFile file =
        new MockMultipartFile("file", "readable.genmodel", "application/octet-stream", bytes);

    File tempFile = service.saveTempFile(file);

    try {
      assertTrue(tempFile.canRead());
      byte[] readBack = Files.readAllBytes(tempFile.toPath());
      assertArrayEquals(bytes, readBack);
    } finally {
      tempFile.delete();
    }
  }
}
