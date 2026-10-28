package tools.vitruv.methodologist.setup.vsum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

class VsumBuildRequestTest {

  @Test
  void readsAllUploads() {
    VsumBuildRequest request =
        VsumBuildRequest.fromMultipart(
            List.of(file("m.ecore")), List.of(file("r.reactions")), List.of(file("c.ocl")), null);

    assertEquals("m.ecore", request.metamodels().get(0).fileName());
    assertEquals("r.reactions", request.reactions().get(0).fileName());
    assertEquals("c.ocl", request.constraints().get(0).fileName());
    assertTrue(request.viewDefinitions().isEmpty());
  }

  @Test
  void requiresAtLeastOneMetamodel() {
    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> VsumBuildRequest.fromMultipart(List.of(), null, null, null));

    assertEquals(VsumErrorCodes.INPUT_ERROR, exception.getErrorCode());
  }

  @Test
  void rejectsWrongExtensions() {
    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () ->
                VsumBuildRequest.fromMultipart(
                    List.of(file("m.ecore")), List.of(file("r.txt")), null, null));

    assertTrue(exception.getMessage().contains("'.reactions'"), exception.getMessage());
  }

  @Test
  void rejectsEmptyFiles() {
    MockMultipartFile empty = new MockMultipartFile("metamodelFiles", "m.ecore", null, new byte[0]);

    assertThrows(
        MethodologistSetupException.class,
        () -> VsumBuildRequest.fromMultipart(List.of(empty), null, null, null));
  }

  @Test
  void rejectsDuplicateFileNames() {
    assertThrows(
        MethodologistSetupException.class,
        () ->
            VsumBuildRequest.fromMultipart(
                List.of(file("m.ecore"), file("M.ecore")), null, null, null));
  }

  @Test
  void sanitizesClientSuppliedFileNames() {
    assertEquals("evil.ecore", UploadedFile.sanitizeFileName("../../etc/evil.ecore"));
    assertEquals("model.ecore", UploadedFile.sanitizeFileName("C:\\Users\\me\\model.ecore"));
    assertEquals("my_model__1_.ecore", UploadedFile.sanitizeFileName("my model (1).ecore"));
    assertEquals("", UploadedFile.sanitizeFileName(null));
  }

  private static MockMultipartFile file(String name) {
    return new MockMultipartFile("file", name, "text/plain", "content".getBytes());
  }
}
