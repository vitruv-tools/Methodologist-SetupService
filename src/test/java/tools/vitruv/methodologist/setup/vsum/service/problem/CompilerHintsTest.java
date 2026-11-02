package tools.vitruv.methodologist.setup.vsum.service.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompilerHintsTest {

  @Test
  void explainsTheOrderOfImports() {
    assertTrue(
        CompilerHints.forMessage(
                "mismatched input 'java' expecting RULE_STRING", "import java.io.File")
            .startsWith("A metamodel import needs the nsURI in quotes"));
  }

  @Test
  void translatesGrammarTokens() {
    assertEquals(
        "Remove '{', or check what comes before it; the parser expected 'after'",
        CompilerHints.forMessage(
            "extraneous input '{' expecting 'after'", "reaction ComponentRenamed {{"));
    assertEquals(
        "'+' is not allowed here, the parser expected a name; check the keywords and brackets"
            + " just before it",
        CompilerHints.forMessage("mismatched input '+' expecting RULE_ID", "routine +x()"));
    assertEquals(
        "Add '}' before 'routine'; every '{' needs a matching '}'",
        CompilerHints.forMessage("missing '}' at 'routine'", "routine x() {"));
  }

  @Test
  void tellsWhatAnUnresolvedNameShouldBeFromItsPosition() {
    assertTrue(
        CompilerHints.forMessage(
                "Entitiy cannot be resolved.", "    val entity = new model2::Entitiy")
            .startsWith("'Entitiy' is not a class of the metamodel named before '::'"));
    assertTrue(
        CompilerHints.forMessage(
                "nam cannot be resolved.", "after attribute replaced at model::Component[nam]")
            .startsWith("Check the spelling of 'nam'; 'after attribute' needs an attribute"));
    assertEquals(
        "Import the metamodel at the top of the file: import \"<nsURI>\" as cad",
        CompilerHints.forMessage("cad cannot be resolved.", "after element cad::Part created"));
    assertTrue(
        CompilerHints.forMessage("File cannot be resolved.", "new File(\"x\")")
            .startsWith("If 'File' is a Java type, import it"));
    assertTrue(
        CompilerHints.forMessage("entitiy cannot be resolved.", "entitiy.name = component.name")
            .startsWith("'entitiy' is not declared here"));
  }

  @Test
  void explainsXbaseAndValidatorMessages() {
    assertEquals(
        "Values declared with 'val' cannot be changed; declare it with 'var' instead",
        CompilerHints.forMessage("Assignment to final variable", "weight += comp.weight"));
    assertEquals(
        "Use 'replaced at' for single-valued references",
        CompilerHints.forMessage("Element cannot be inserted into single valued reference", ""));
    assertTrue(
        CompilerHints.forMessage(
                "The method createAndRegisterRot(System) is undefined",
                "call createAndRegisterRot(x)")
            .startsWith("There is no routine or method 'createAndRegisterRot'"));
  }

  @Test
  void hasNoHintForUnknownMessages() {
    assertNull(CompilerHints.forMessage("Something entirely new", "x"));
  }
}
