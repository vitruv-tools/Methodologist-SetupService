package tools.vitruv.methodologist.setup.vsum.service.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem.Source;

class ProblemReportTest {

  @Test
  void groupsProblemsByFileWithLocationExcerptHintAndHiddenErrors() {
    List<BuildProblem> problems =
        List.of(
            BuildProblem.at(
                    Source.REACTIONS,
                    "a.reactions",
                    "import \"x\" as x\nimport java.io.File\n",
                    2,
                    8,
                    "mismatched input 'java' expecting RULE_STRING")
                .withHiddenFollowUpErrors(3),
            BuildProblem.of(
                    Source.METAMODEL,
                    "m.ecore",
                    "Protocol: a class may not be its own" + " super type")
                .withHint("Remove Protocol from its super types"),
            BuildProblem.of(Source.INTERNAL, null, "the smoke test failed"),
            BuildProblem.of(Source.REACTIONS, "a.reactions", "second").withHiddenFollowUpErrors(1));

    assertEquals(
        """
        The VSUM build failed:

        a.reactions:
        - line 2, column 8: mismatched input 'java' expecting RULE_STRING
              import java.io.File
                     ^
          (3 follow-up errors hidden)
        - second
          (1 follow-up error hidden)

        m.ecore:
        - Protocol: a class may not be its own super type
          Hint: Remove Protocol from its super types

        Setup service:
        - the smoke test failed""",
        ProblemReport.render("The VSUM build failed:", problems));
  }

  @Test
  void excerptsKeepTheCaretUnderTheColumnAfterTabs() {
    assertEquals("    call x()\n         ^", Excerpts.of("first\n\tcall x()\n", 2, 7));
  }

  @Test
  void excerptsOfUnknownLinesAreAbsent() {
    assertNull(Excerpts.of("one line", 5, 1));
    assertNull(Excerpts.of(null, 1, 1));
    assertEquals("one line", Excerpts.of("one line", 1, null));
  }

  @Test
  void computesLineAndColumnOfAnOffset() {
    String text = "ab\ncd\nef";

    assertEquals(3, Excerpts.lineOf(text, 7));
    assertEquals(2, Excerpts.columnOf(text, 7));
    assertEquals(1, Excerpts.columnOf(text, 0));
  }
}
