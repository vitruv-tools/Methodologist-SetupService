package tools.vitruv.methodologist.setup.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReactionSpecificationsTest {
  @TempDir Path directory;

  @Test
  void readsMultipleSegmentsIgnoringCommentsAndStrings() throws Exception {
    Path source = directory.resolve("unrelated-filename.reactions");
    Files.writeString(
        source,
        """
        // reactions: FakeComment
        /* reactions: FakeDocumentation */
        import "http://example/reactions:FakeUri" as source
        reactions: CAD2Brake
        in reaction to changes in source
        execute actions in source
        "reactions: FakeString"
        'reactions: FakeSingleQuotedString'
        \u0027\u0027\u0027reactions: FakeRichString\u0027\u0027\u0027
        reactions: brake2CAD
        in reaction to changes in source
        execute actions in source
        """);
    assertThat(ReactionSpecifications.read(List.of(source, source)))
        .containsExactly(
            "mir.reactions.cAD2Brake.CAD2BrakeChangePropagationSpecification",
            "mir.reactions.brake2CAD.Brake2CADChangePropagationSpecification");
  }

  @Test
  void supportsNoReactions() throws Exception {
    assertThat(ReactionSpecifications.read(List.of())).isEmpty();
  }
}
