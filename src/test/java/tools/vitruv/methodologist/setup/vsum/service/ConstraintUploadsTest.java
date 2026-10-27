package tools.vitruv.methodologist.setup.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;

class ConstraintUploadsTest {
  @TempDir Path tempDir;

  @Test
  void namesAreSafeAndDistinct() throws Exception {
    List<File> files =
        ConstraintUploads.write(
            tempDir,
            List.of(
                upload("a-1-3.ocl"),
                upload("a-1.ocl"),
                upload("x.ocl"),
                upload("a-1.ocl"),
                upload("A-1.OCL"),
                upload("../../etc/passwd"),
                upload("dir/sub\\name with spaces.ocl"),
                upload("/"),
                upload("..")));

    List<String> names = files.stream().map(File::getName).toList();
    Set<String> lowerCase =
        names.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    assertThat(lowerCase).hasSize(names.size());
    assertThat(names)
        .allMatch(name -> name.endsWith(".ocl") && !name.matches(".*[\\\\/:*?\"<>|].*"));
    assertThat(names).noneMatch(name -> name.startsWith("."));
    assertThat(files).allMatch(file -> file.toPath().getParent().equals(tempDir));
    assertThat(names).contains("_.._etc_passwd.ocl", "dir_sub_name with spaces.ocl");
  }

  @Test
  void keepsRuleSetNames() throws Exception {
    List<File> files =
        ConstraintUploads.write(
            tempDir,
            List.of(
                upload("Preise & Regeln.ocl"),
                upload("Komponenten (Benennung).ocl"),
                upload("con.ocl"),
                upload("what?.ocl")));

    assertThat(files)
        .extracting(File::getName)
        .containsExactly(
            "Preise & Regeln.ocl", "Komponenten (Benennung).ocl", "_con.ocl", "what_.ocl");
  }

  @Test
  void keepsGroupingOfRuleSetNamesWithSlashes() throws Exception {
    List<File> files =
        ConstraintUploads.write(tempDir, List.of(upload("A/Regeln.ocl"), upload("B/Regeln.ocl")));

    assertThat(files).extracting(File::getName).containsExactly("A_Regeln.ocl", "B_Regeln.ocl");
  }

  @Test
  void shortensLongNames() throws Exception {
    String longName = "Ä".repeat(300) + ".ocl";

    File file = ConstraintUploads.write(tempDir, List.of(upload(longName))).get(0);

    assertThat(file.getName()).isEqualTo("Ä".repeat(100) + ".ocl");
  }

  @Test
  void ignoresPartsWithoutFile() throws Exception {
    MockMultipartFile noFile =
        new MockMultipartFile("constraintFiles", "", "application/octet-stream", new byte[0]);

    assertThat(ConstraintUploads.write(tempDir, List.of(noFile))).isEmpty();
  }

  @Test
  void rejectsEmptyFiles() {
    MockMultipartFile empty =
        new MockMultipartFile("constraintFiles", "empty.ocl", "text/plain", new byte[0]);

    assertThatThrownBy(() -> ConstraintUploads.write(tempDir, List.of(empty)))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("empty.ocl");
  }

  @Test
  void acceptsKnownQualifiedContexts() {
    assertThatCode(
            () ->
                check(
                    """
                    context model::Component inv a: true
                    context c : model::Link inv b: true
                    """))
        .doesNotThrowAnyException();
  }

  @Test
  void ignoresContextsInCommentsAndStrings() {
    assertThatCode(
            () ->
                check(
                    """
                    -- context shop::Item inv old: true
                    /* context shop::Item
                       inv older: true */
                    context model::Component inv a:
                      @message "see context shop::Item -- not a context"
                      true
                    """))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsUnknownMetamodels() {
    assertThatThrownBy(() -> check("context shop::Item inv a: true"))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("shop");
  }

  @Test
  void rejectsUnqualifiedContexts() {
    assertThatThrownBy(() -> check("context Component inv a: true"))
        .isInstanceOf(MethodologistSetupException.class)
        .hasMessageContaining("Component")
        .hasMessageContaining("without metamodel");
  }

  private void check(String content) throws Exception {
    File file = Files.writeString(tempDir.resolve("c.ocl"), content).toFile();
    ConstraintUploads.checkMetamodels(List.of(file), Set.of("model"));
  }

  private static MockMultipartFile upload(String name) {
    return new MockMultipartFile(
        "constraintFiles",
        name,
        "text/plain",
        "context model::Component inv a: true".getBytes(StandardCharsets.UTF_8));
  }
}
