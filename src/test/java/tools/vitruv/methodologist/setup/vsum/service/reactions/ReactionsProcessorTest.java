package tools.vitruv.methodologist.setup.vsum.service.reactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.fixture;
import static tools.vitruv.methodologist.setup.vsum.service.TestFiles.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.metamodel.GeneratedMetamodel;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

class ReactionsProcessorTest {

  private static final List<GeneratedMetamodel> METAMODELS =
      List.of(
          metamodel("model", "http://vitruv.tools/methodologisttemplate/model"),
          metamodel("model2", "http://vitruv.tools/methodologisttemplate/model2"));

  private final ReactionsProcessor processor = new ReactionsProcessor();

  @TempDir Path projectRoot;

  @Test
  void derivesTheGeneratedChangePropagationSpecifications() throws IOException {
    List<String> specifications =
        processor.process(List.of(fixture("templateReactions.reactions")), METAMODELS, projectRoot);

    assertEquals(
        List.of("mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification"),
        specifications);
    assertTrue(
        Files.isRegularFile(
            ProjectLayout.reactionsDirectory(projectRoot).resolve("templateReactions.reactions")));
  }

  @Test
  void followsTheReactionsClassNaming() {
    assertEquals(
        "mir.reactions.umlToJava.UmlToJavaChangePropagationSpecification",
        ReactionsProcessor.changePropagationSpecification("UmlToJava"));
  }

  @Test
  void supportsSeveralSegmentsAndIgnoresCommentedOutCode() throws IOException {
    UploadedFile reactions =
        text(
            "multi.reactions",
            """
            import "http://vitruv.tools/methodologisttemplate/model" as model // "http://ignored"
            /* reactions: commentedOut
               import "http://not/uploaded" as x */
            reactions: first
            in reaction to changes in model
            execute actions in model
            reactions: second
            in reaction to changes in model
            execute actions in model
            """);

    List<String> specifications = processor.process(List.of(reactions), METAMODELS, projectRoot);

    assertEquals(
        List.of(
            "mir.reactions.first.FirstChangePropagationSpecification",
            "mir.reactions.second.SecondChangePropagationSpecification"),
        specifications);
  }

  @Test
  void ignoresSegmentDeclarationsInsideCommentsAndStringLiterals() throws IOException {
    UploadedFile reactions =
        text(
            "unrelated-filename.reactions",
            """
            // reactions: FakeComment
            /* reactions: FakeDocumentation */
            import "http://vitruv.tools/methodologisttemplate/model" as source
            reactions: CAD2Brake
            in reaction to changes in source
            execute actions in source
            "reactions: FakeString"
            'reactions: FakeSingleQuotedString // not a comment'
            \u0027\u0027\u0027reactions: FakeRichString\u0027\u0027\u0027
            reactions: ^brake2CAD
            in reaction to changes in source
            execute actions in source
            """);

    List<String> specifications = processor.process(List.of(reactions), METAMODELS, projectRoot);

    assertEquals(
        List.of(
            "mir.reactions.cAD2Brake.CAD2BrakeChangePropagationSpecification",
            "mir.reactions.brake2CAD.Brake2CADChangePropagationSpecification"),
        specifications);
  }

  @Test
  void rejectsImportsOfMetamodelsThatWereNotUploadedAndSuggestsTheRightOne() {
    UploadedFile reactions =
        text(
            "wrong.reactions",
            """
            import "http://example.org/model2" as model2
            reactions: wrong
            """);

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> processor.process(List.of(reactions), METAMODELS, projectRoot));

    assertEquals(VsumErrorCodes.REACTIONS_ERROR, exception.getErrorCode());
    assertTrue(
        exception
            .getMessage()
            .contains("did you mean 'http://vitruv.tools/methodologisttemplate/model2'"),
        exception.getMessage());
  }

  @Test
  void rejectsFilesWithoutSegment() {
    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () ->
                processor.process(
                    List.of(text("empty.reactions", "// nothing here")), METAMODELS, projectRoot));

    assertTrue(exception.getMessage().contains("empty.reactions"), exception.getMessage());
  }

  @Test
  void rejectsSegmentsDeclaredTwice() {
    UploadedFile first = text("a.reactions", "reactions: same");
    UploadedFile second = text("b.reactions", "reactions: Same");

    MethodologistSetupException exception =
        assertThrows(
            MethodologistSetupException.class,
            () -> processor.process(List.of(first, second), METAMODELS, projectRoot));

    assertTrue(
        exception.getMessage().contains("already declared in a.reactions"), exception.getMessage());
  }

  private static GeneratedMetamodel metamodel(String name, String nsUri) {
    return new GeneratedMetamodel(
        name,
        nsUri,
        name + ".ecore",
        name + ".genmodel",
        List.of(new GeneratedMetamodel.GeneratedPackage(nsUri, "x." + name + ".Package")));
  }
}
