package tools.vitruv.methodologist.setup.vsum.service.problem;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explains what to change for the messages of the Reactions compiler (Xtext parser, Xbase type
 * checker, and Reactions validator), whose wording assumes knowledge of the grammar.
 */
public final class CompilerHints {

  private record Rule(Pattern message, BiFunction<Matcher, String, String> hint) {}

  private static final Map<String, String> TOKENS =
      Map.of("RULE_ID", "a name", "RULE_STRING", "a quoted string", "RULE_INT", "a number");

  private static final List<Rule> RULES =
      List.of(
          rule(
              "mismatched input '(.*)' expecting RULE_STRING",
              (m, line) ->
                  line.strip().startsWith("import")
                      ? "A metamodel import needs the nsURI in quotes: import \"http://…\" as"
                          + " alias. Java imports such as 'import java.util.List' must come before"
                          + " the metamodel imports"
                      : "A quoted string is expected before '" + m.group(1) + "'"),
          rule(
              "mismatched input '(.*)' expecting (.*)",
              (m, line) ->
                  String.format(
                      "'%s' is not allowed here, the parser expected %s; check the keywords and"
                          + " brackets just before it",
                      m.group(1), expected(m.group(2)))),
          rule(
              "extraneous input '(.*)' expecting (.*)",
              (m, line) ->
                  String.format(
                      "Remove '%s', or check what comes before it; the parser expected %s",
                      m.group(1), expected(m.group(2)))),
          rule(
              "missing EOF at '(.*)'",
              (m, line) ->
                  "Only reactions, routines, and further segments may follow a segment; check for"
                      + " an extra '}' before '"
                      + m.group(1)
                      + "'"),
          rule(
              "missing (.+) at '(.*)'",
              (m, line) ->
                  String.format("Add %s before '%s'", expected(m.group(1)), m.group(2))
                      + (m.group(1).equals("'}'") ? "; every '{' needs a matching '}'" : "")),
          rule(
              "no viable alternative at input '(.*)'",
              (m, line) ->
                  "'"
                      + m.group(1)
                      + "' cannot be parsed here; compare it with the grammar, e.g. 'after element"
                      + " alias::Class inserted in alias::Container[feature]'"),
          rule(
              "Couldn't resolve reference to MetamodelImport '(.*)'\\.?",
              (m, line) -> importAlias(m.group(1))),
          rule(
              "Couldn't resolve reference to EClassifier '(.*)'\\.?",
              (m, line) -> metaclass(m.group(1))),
          rule(
              "Couldn't resolve reference to (?:EAttribute|EReference|EStructuralFeature)"
                  + " '(.*)'\\.?",
              (m, line) -> feature(m.group(1))),
          rule(
              "Couldn't resolve reference to ReactionsSegment '(.*)'\\.?",
              (m, line) ->
                  "Only reactions segments declared in the uploaded reactions can be imported or"
                      + " overridden; check the spelling of '"
                      + m.group(1)
                      + "'"),
          rule(
              "Couldn't resolve reference to JvmType '(.*)'\\.?",
              (m, line) -> javaType(m.group(1))),
          rule("(\\S+) cannot be resolved to a type\\.?", (m, line) -> javaType(m.group(1))),
          rule("(\\S+) cannot be resolved\\.?", CompilerHints::unresolved),
          rule(
              "The method or field (\\S+) is undefined for the type (\\S+)",
              (m, line) ->
                  String.format(
                      "'%s' has no feature or method '%s'; check the spelling against the"
                          + " metamodel, features are accessed by name, e.g. component.name",
                      m.group(2), m.group(1))),
          rule(
              "The method (\\S+)\\((.*)\\) is undefined for the type (\\S+)",
              (m, line) ->
                  String.format(
                      "'%s' has no method '%s' for these arguments; check the spelling and the"
                          + " argument types",
                      m.group(3), m.group(1))),
          rule(
              "The method (\\S+)\\((.*)\\) is undefined",
              (m, line) ->
                  String.format(
                      "There is no routine or method '%s' for these arguments; check the spelling"
                          + " and that the routine is declared in this reactions segment",
                      m.group(1))),
          rule(
              "Invalid number of arguments.*",
              (m, line) -> "Pass exactly the parameters the routine or method declares"),
          rule(
              "Type mismatch: cannot convert from (.+) to (.+)",
              (m, line) ->
                  String.format(
                      "The expression has the type %s where %s is needed; use a value of type %s"
                          + " or convert it",
                      m.group(1), m.group(2), m.group(2))),
          rule(
              "(?:Assignment to final (?:variable|field)|Cannot assign a value to final"
                  + " variable).*",
              (m, line) ->
                  "Values declared with 'val' cannot be changed; declare it with 'var' instead"),
          rule(
              "Duplicate local variable (\\S+)",
              (m, line) -> "Rename one of them; every 'val' and 'var' needs its own name"),
          rule(
              "Duplicate (reaction|routine) name: (.+)",
              (m, line) ->
                  "Rename one of them; " + m.group(1) + " names must be unique in a segment"),
          rule(
              "Element cannot be (?:inserted into|removed from) single valued reference",
              (m, line) -> "Use 'replaced at' for single-valued references"),
          rule(
              "Element of specified type cannot be contained in the specified features",
              (m, line) -> "Use the type of the reference or one of its subclasses"),
          rule(
              "(?:Reactions segment|Routine|Element) names must not start with an underscore\\.?",
              (m, line) -> "Rename it without the leading '_'"));

  private static final Pattern METACLASS_BEFORE = Pattern.compile("::\\s*\\^?$");
  private static final Pattern FEATURE_BEFORE = Pattern.compile("\\[\\s*\\^?$");

  private CompilerHints() {}

  /**
   * Returns a hint for a compiler message.
   *
   * @param message the compiler message, without location
   * @param line the source line the message refers to, or {@code null}
   * @return what to change, or {@code null} if the message is not known
   */
  public static String forMessage(String message, String line) {
    String trimmed = message.strip();
    String source = line == null ? "" : line;
    for (Rule rule : RULES) {
      Matcher matcher = rule.message().matcher(trimmed);
      if (matcher.matches()) {
        return rule.hint().apply(matcher, source);
      }
    }
    return null;
  }

  /** Xbase reports any unresolvable name alike; the text before it tells what it should be. */
  private static String unresolved(Matcher matcher, String line) {
    String name = matcher.group(1);
    int at = line.indexOf(name);
    String before = at < 0 ? "" : line.substring(0, at);
    if (METACLASS_BEFORE.matcher(before).find()) {
      return metaclass(name);
    }
    if (FEATURE_BEFORE.matcher(before).find()) {
      return feature(name);
    }
    if (line.matches(".*\\b" + Pattern.quote(name) + "\\s*::.*")) {
      return importAlias(name);
    }
    return Character.isUpperCase(name.charAt(0))
        ? javaType(name)
        : String.format(
            "'%s' is not declared here; check the spelling and whether it is a parameter of the"
                + " routine or a 'val' of its match or create block",
            name);
  }

  private static String metaclass(String name) {
    return String.format(
        "'%s' is not a class of the metamodel named before '::'; check the spelling and the"
            + " alias",
        name);
  }

  private static String feature(String name) {
    return String.format(
        "Check the spelling of '%s'; 'after attribute' needs an attribute, 'after element' a"
            + " reference, and 'replaced at' a single-valued one",
        name);
  }

  private static String importAlias(String name) {
    return String.format(
        "Import the metamodel at the top of the file: import \"<nsURI>\" as %s", name);
  }

  private static String javaType(String name) {
    return String.format(
        "If '%s' is a Java type, import it at the top of the file, before the metamodel imports"
            + " (e.g. 'import java.io.File'); a metamodel class is written as alias::%s",
        name, name);
  }

  private static String expected(String expected) {
    String result = expected;
    for (Map.Entry<String, String> token : TOKENS.entrySet()) {
      result = result.replace(token.getKey(), token.getValue());
    }
    return result.replace("EOF", "the end of the file");
  }

  private static Rule rule(String message, BiFunction<Matcher, String, String> hint) {
    return new Rule(Pattern.compile(message, Pattern.DOTALL), hint);
  }
}
