package tools.vitruv.methodologist.setup.vsum.service.reactions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.problem.BuildProblem;

/**
 * Checks the metamodel references of a Reactions file against the uploaded metamodels, the way the
 * Reactions scope provider and validator resolve them: metamodel aliases, {@code alias::Class},
 * {@code alias::Class[feature]} in triggers, and the classes created in {@code create} blocks.
 *
 * <p>The check works on the text, so it reports only what is certainly wrong and leaves everything
 * else (Xbase code in particular) to the Reactions compiler. It answers in milliseconds, before the
 * Maven build, and suggests the intended name.
 */
final class ReactionsChecker {

  // Quantifiers are possessive: each repetition is followed by something it cannot consume, so
  // backtracking would never find another match, and possessive matching cannot overflow the stack.
  private static final String NAME = "\\^?[A-Za-z_]\\w*+";
  private static final String ID = "\\^?([A-Za-z_]\\w*+)";
  private static final String SEPARATOR = "\\s*+::\\s*+";
  private static final String QUALIFIED_NAME = NAME + "(?:\\s*+\\.\\s*+" + NAME + ")*+";
  private static final String REPLACED_AT = "replaced at";
  private static final String INSERTED_IN = "inserted in";
  private static final Pattern ALIAS = Pattern.compile(ID);
  private static final Pattern METAMODEL_IMPORT =
      Pattern.compile(
          "\\bimport\\s++\"([^\"]*+)\"\\s++as\\s++"
              + ID
              + "(\\s++using\\s++qualified\\s++names)?+");
  private static final Pattern JAVA_IMPORT_NAME =
      Pattern.compile(
          "\\bimport\\s++(?:static\\s++(?:extension\\s++)?+)?+(?:"
              + NAME
              + "\\s*+\\.\\s*+)*+"
              + ID);
  private static final Pattern SEGMENT_METAMODELS =
      Pattern.compile(
          "\\b(?:in\\s++reaction\\s++to\\s++changes\\s++in|execute\\s++actions\\s++in)\\s++("
              + NAME
              + "(?:\\s++and\\s++"
              + NAME
              + ")*+)");
  // a metaclass reference; the lookbehind skips qualified Java types like java.util.List::of
  private static final Pattern METACLASS =
      Pattern.compile("(?<![\\w.$^])" + ID + SEPARATOR + "(" + QUALIFIED_NAME + ")");
  // 'reaction segment::Name' and 'routine a.b::name' override other segments, they are no
  // metaclasses
  private static final Pattern OVERRIDE_BEFORE =
      Pattern.compile("\\b(?:reaction|routine)\\s++(?:" + NAME + "\\s*+\\.\\s*+)*+$");
  // a trigger is matched in parts: after element|attribute [alias::Class] change alias::Class[f]
  private static final Pattern TRIGGER_START =
      Pattern.compile("\\bafter\\s++(element|attribute)\\b");
  private static final Pattern TRIGGER_ELEMENT =
      Pattern.compile("\\s++" + ID + SEPARATOR + "(" + QUALIFIED_NAME + ")");
  private static final Pattern TRIGGER_CHANGE =
      Pattern.compile("\\s++(inserted\\s++in|removed\\s++from|replaced\\s++at)\\b");
  private static final Pattern TRIGGER_FEATURE =
      Pattern.compile(
          "\\s++" + ID + SEPARATOR + "(" + QUALIFIED_NAME + ")\\s*+\\[\\s*+" + ID + "\\s*+\\]");
  // 'new' with '::' only occurs in create blocks; Xbase constructor calls use Java types
  private static final Pattern CREATE =
      Pattern.compile("\\bnew\\s++" + ID + SEPARATOR + "(" + QUALIFIED_NAME + ")");

  private final UploadedFile file;
  private final String code;
  private final Map<String, Alias> aliases = new LinkedHashMap<>();
  private final Set<String> javaTypes = new HashSet<>();
  private final Collection<EPackage> allPackages;
  private final List<BuildProblem> problems = new ArrayList<>();

  private record Alias(String name, EPackage ePackage, boolean qualifiedNames) {}

  /**
   * A trigger on a feature, e.g. {@code after element a::X inserted in a::Y[f]}.
   *
   * @param attributeChange whether it is an {@code after attribute} trigger
   * @param elementAlias alias of the element type, or {@code null} if there is none
   * @param elementName name of the element type, or {@code null}
   * @param elementOffset offset of the element type name, or -1
   * @param change the change with single spaces, e.g. "inserted in"
   * @param alias alias of the class holding the feature
   * @param className name of the class holding the feature
   * @param feature the feature name
   * @param featureOffset offset of the feature name
   */
  private record Trigger(
      boolean attributeChange,
      String elementAlias,
      String elementName,
      int elementOffset,
      String change,
      String alias,
      String className,
      String feature,
      int featureOffset) {}

  private ReactionsChecker(UploadedFile file, Collection<EPackage> allPackages) {
    this.file = file;
    this.code = ReactionsProcessor.blankStrings(ReactionsProcessor.stripComments(file.text()));
    this.allPackages = allPackages;
  }

  /**
   * Checks the metamodel references of a reactions file.
   *
   * @param file the uploaded reactions file
   * @param packagesByNsUri all packages the reactions may import, including subpackages and Ecore
   * @return the problems found; imports of unknown nsURIs are not reported here
   */
  static List<BuildProblem> check(UploadedFile file, Map<String, EPackage> packagesByNsUri) {
    ReactionsChecker checker = new ReactionsChecker(file, Set.copyOf(packagesByNsUri.values()));
    checker.readImports(packagesByNsUri);
    checker.checkSegmentMetamodels();
    checker.checkMetaclasses();
    checker.checkTriggers();
    checker.checkCreatedClasses();
    return grouped(checker.problems);
  }

  /** Reports a repeated mistake, such as a misspelled alias, once with the other lines. */
  private static List<BuildProblem> grouped(List<BuildProblem> problems) {
    Map<String, List<BuildProblem>> byMessage = new LinkedHashMap<>();
    problems.stream()
        .sorted(Comparator.comparing(BuildProblem::line).thenComparing(BuildProblem::column))
        .forEach(
            problem ->
                byMessage
                    .computeIfAbsent(problem.message(), key -> new ArrayList<>())
                    .add(problem));
    return byMessage.values().stream()
        .map(
            group -> {
              BuildProblem first = group.get(0);
              List<String> otherLines =
                  group.stream()
                      .map(BuildProblem::line)
                      .filter(line -> !line.equals(first.line()))
                      .distinct()
                      .map(String::valueOf)
                      .toList();
              return otherLines.isEmpty()
                  ? first
                  : first.withMessage(
                      first.message() + " (also in lines " + String.join(", ", otherLines) + ")");
            })
        .toList();
  }

  private void readImports(Map<String, EPackage> packagesByNsUri) {
    // string literals are blanked in the code, so imports are read from the text with comments
    Matcher imports = METAMODEL_IMPORT.matcher(ReactionsProcessor.stripComments(file.text()));
    while (imports.find()) {
      aliases.put(
          imports.group(2),
          new Alias(
              imports.group(2), packagesByNsUri.get(imports.group(1)), imports.group(3) != null));
    }
    Matcher javaImports = JAVA_IMPORT_NAME.matcher(code);
    while (javaImports.find()) {
      javaTypes.add(javaImports.group(1));
    }
  }

  private void checkSegmentMetamodels() {
    Matcher lists = SEGMENT_METAMODELS.matcher(code);
    while (lists.find()) {
      Matcher names = ALIAS.matcher(lists.group(1));
      while (names.find()) {
        String name = names.group(1);
        if (!name.equals("and") && !aliases.containsKey(name)) {
          unknownAlias(name, lists.start(1) + names.start(1));
        }
      }
    }
  }

  private void checkMetaclasses() {
    Matcher references = METACLASS.matcher(code);
    while (references.find()) {
      if (!isOverride(references.start())) {
        checkMetaclass(
            references.group(1), references.start(1), references.group(2), references.start(2));
      }
    }
  }

  private boolean isOverride(int offset) {
    return OVERRIDE_BEFORE.matcher(code.substring(Math.max(0, offset - 200), offset)).find();
  }

  private void checkMetaclass(String aliasName, int aliasOffset, String name, int nameOffset) {
    Alias alias = aliases.get(aliasName);
    if (alias != null) {
      resolve(alias, name, nameOffset);
    } else if (Character.isLowerCase(aliasName.charAt(0)) && !javaTypes.contains(aliasName)) {
      // 'Type::member' is static member access in Xbase; aliases are lower case by convention
      unknownAlias(aliasName, aliasOffset);
    }
  }

  private void checkTriggers() {
    Matcher starts = TRIGGER_START.matcher(code);
    while (starts.find()) {
      Trigger trigger = trigger(starts);
      if (trigger != null) {
        checkTrigger(trigger);
      }
    }
  }

  /** Reads the trigger after {@code after element|attribute}, or {@code null} if it has none. */
  private Trigger trigger(Matcher start) {
    Matcher element = TRIGGER_ELEMENT.matcher(code).region(start.end(), code.length());
    boolean typed = element.lookingAt();
    Matcher change =
        TRIGGER_CHANGE.matcher(code).region(typed ? element.end() : start.end(), code.length());
    if (!change.lookingAt()) {
      return null;
    }
    Matcher feature = TRIGGER_FEATURE.matcher(code).region(change.end(), code.length());
    if (!feature.lookingAt()) {
      return null;
    }
    return new Trigger(
        start.group(1).equals("attribute"),
        typed ? element.group(1) : null,
        typed ? element.group(2) : null,
        typed ? element.start(2) : -1,
        change.group(1).replaceAll("\\s++", " "),
        feature.group(1),
        feature.group(2),
        feature.group(3),
        feature.start(3));
  }

  private void checkTrigger(Trigger trigger) {
    if (!(resolveQuietly(trigger.alias(), trigger.className()) instanceof EClass owner)) {
      return; // unresolved classes are reported by checkMetaclasses
    }
    EStructuralFeature feature = owner.getEStructuralFeature(trigger.feature());
    if (feature == null) {
      reportMissingFeature(trigger, owner);
    } else if (trigger.attributeChange() != feature instanceof EAttribute) {
      reportWrongFeatureKind(trigger, owner, feature);
    } else if (feature instanceof EReference reference) {
      checkElementChange(trigger, reference);
    }
  }

  private void reportMissingFeature(Trigger trigger, EClass owner) {
    String kind = trigger.attributeChange() ? "attribute" : "reference";
    List<String> candidates =
        owner.getEAllStructuralFeatures().stream()
            .filter(feature -> trigger.attributeChange() == feature instanceof EAttribute)
            .map(ENamedElement::getName)
            .toList();
    add(
        trigger.featureOffset(),
        String.format("'%s' has no %s '%s'", owner.getName(), kind, trigger.feature()),
        suggestion(trigger.feature(), candidates, kind + "s"));
  }

  private void reportWrongFeatureKind(Trigger trigger, EClass owner, EStructuralFeature feature) {
    String name = owner.getName() + "." + trigger.feature();
    if (feature instanceof EReference) {
      add(
          trigger.featureOffset(),
          String.format("'%s' is a reference, not an attribute", name),
          String.format(
              "Use 'after element %s %s[%s]'",
              elementChangeFor(trigger.change(), feature), qualified(owner), trigger.feature()));
    } else {
      add(
          trigger.featureOffset(),
          String.format("'%s' is an attribute, not a reference", name),
          String.format(
              "Use 'after attribute %s %s[%s]'",
              trigger.change(), qualified(owner), trigger.feature()));
    }
  }

  /** The element change that fits the multiplicity of a reference. */
  private static String elementChangeFor(String change, EStructuralFeature reference) {
    if (!reference.isMany()) {
      return REPLACED_AT;
    }
    return REPLACED_AT.equals(change) ? INSERTED_IN : change;
  }

  private void checkElementChange(Trigger trigger, EReference reference) {
    String name = reference.getEContainingClass().getName() + "." + reference.getName();
    boolean replaced = REPLACED_AT.equals(trigger.change());
    EClassifier elementType = resolveQuietly(trigger.elementAlias(), trigger.elementName());
    if (replaced && reference.isMany()) {
      add(
          trigger.featureOffset(),
          String.format("'%s' is many-valued, so its elements are not replaced", name),
          "Use 'inserted in' and 'removed from' for many-valued references");
    } else if (!replaced && !reference.isMany()) {
      add(
          trigger.featureOffset(),
          String.format(
              "'%s' is single-valued, so elements cannot be %s it", name, trigger.change()),
          "Use 'replaced at' for single-valued references");
    } else if (elementType instanceof EClass elementClass
        && reference.getEReferenceType() != null
        && !related(elementClass, reference.getEReferenceType())) {
      add(
          trigger.elementOffset(),
          String.format(
              "elements of type '%s' cannot be contained in '%s', which holds '%s'",
              elementClass.getName(), name, reference.getEReferenceType().getName()),
          String.format(
              "Use '%s' or one of its subclasses or super classes",
              reference.getEReferenceType().getName()));
    }
  }

  private void checkCreatedClasses() {
    Matcher creations = CREATE.matcher(code);
    while (creations.find()) {
      EClassifier classifier = resolveQuietly(creations.group(1), creations.group(2));
      int offset = creations.start(2);
      if (classifier instanceof EEnum) {
        add(
            offset,
            String.format("'%s' is an enum, so it cannot be created", classifier.getName()),
            "Use one of its literals in the update block instead");
      } else if (classifier instanceof EClass eClass
          && (eClass.isAbstract() || eClass.isInterface())) {
        List<String> concrete =
            allClasses()
                .filter(candidate -> !candidate.isAbstract() && !candidate.isInterface())
                .filter(candidate -> candidate.getEAllSuperTypes().contains(eClass))
                .map(EClass::getName)
                .sorted()
                .toList();
        add(
            offset,
            String.format(
                "'%s' is %s, so it cannot be created",
                eClass.getName(), eClass.isInterface() ? "an interface" : "abstract"),
            concrete.isEmpty()
                ? "Make the class non-abstract in the metamodel, or create a concrete subclass"
                : "Create one of its concrete subclasses: " + String.join(", ", concrete));
      }
    }
  }

  /** Resolves {@code alias::Name} like the Reactions scope provider and reports failures. */
  private void resolve(Alias alias, String rawName, int offset) {
    if (alias.ePackage() == null) {
      return; // the import itself is reported
    }
    String name = rawName.replaceAll("[\\s^]", "");
    if (lookup(alias, name) == null) {
      String reported = alias.qualifiedNames() ? name : name.split("\\.")[0];
      add(
          offset,
          String.format(
              "'%s' is not a class of the metamodel '%s' (%s)",
              reported, alias.name(), alias.ePackage().getNsURI()),
          suggestion(reported, classifiers(alias).keySet(), "classes"));
    }
  }

  /** Resolves {@code alias::Name} without reporting; {@code null} if it does not resolve. */
  private EClassifier resolveQuietly(String aliasName, String rawName) {
    Alias alias = aliasName == null ? null : aliases.get(aliasName);
    return alias == null || alias.ePackage() == null
        ? null
        : lookup(alias, rawName.replaceAll("[\\s^]", ""));
  }

  /**
   * Looks up a name written after {@code alias::}. The name may run on into Xbase code, e.g. {@code
   * alias::Class.literal}, so only the leading segments that name a classifier count.
   */
  private static EClassifier lookup(Alias alias, String name) {
    Map<String, EClassifier> classifiers = classifiers(alias);
    if (!alias.qualifiedNames()) {
      return classifiers.get(name.split("\\.")[0]);
    }
    for (String candidate = name;
        ;
        candidate = candidate.substring(0, candidate.lastIndexOf('.'))) {
      EClassifier classifier = classifiers.get(candidate);
      if (classifier != null || candidate.indexOf('.') < 0) {
        return classifier;
      }
    }
  }

  /** Classes and enums visible through an import, keyed as they are written after {@code ::}. */
  private static Map<String, EClassifier> classifiers(Alias alias) {
    Map<String, EClassifier> classifiers = new LinkedHashMap<>();
    collect(
        alias.ePackage(), alias.qualifiedNames() ? qualifier(alias.ePackage()) : null, classifiers);
    return classifiers;
  }

  private static void collect(EPackage ePackage, String qualifier, Map<String, EClassifier> into) {
    for (EClassifier classifier : ePackage.getEClassifiers()) {
      if (classifier instanceof EClass || classifier instanceof EEnum) {
        into.putIfAbsent(
            qualifier == null || qualifier.isEmpty()
                ? classifier.getName()
                : qualifier + "." + classifier.getName(),
            classifier);
      }
    }
    for (EPackage subpackage : ePackage.getESubpackages()) {
      collect(subpackage, childQualifier(qualifier, subpackage), into);
    }
  }

  /** The qualifier of a subpackage's classifiers, or {@code null} without qualified names. */
  private static String childQualifier(String qualifier, EPackage subpackage) {
    if (qualifier == null) {
      return null;
    }
    return qualifier.isEmpty() ? subpackage.getName() : qualifier + "." + subpackage.getName();
  }

  /** Qualified names skip the root package, e.g. {@code sub.Class} for the root's subpackage. */
  private static String qualifier(EPackage ePackage) {
    List<String> names = new ArrayList<>();
    for (EPackage current = ePackage;
        current.getESuperPackage() != null;
        current = current.getESuperPackage()) {
      names.add(0, current.getName());
    }
    return String.join(".", names);
  }

  private void unknownAlias(String name, int offset) {
    add(
        offset,
        String.format("'%s' is not a metamodel imported in this file", name),
        aliases.isEmpty()
            ? String.format("Import it first: import \"<nsURI>\" as %s", name)
            : suggestion(name, aliases.keySet(), "imported metamodels"));
  }

  private Stream<EClass> allClasses() {
    return allPackages.stream()
        .flatMap(ePackage -> ePackage.getEClassifiers().stream())
        .filter(EClass.class::isInstance)
        .map(EClass.class::cast)
        .distinct();
  }

  private static boolean related(EClass first, EClass second) {
    return first == second
        || first.eIsProxy()
        || second.eIsProxy()
        || first.getEAllSuperTypes().contains(second)
        || second.getEAllSuperTypes().contains(first)
        || isEObject(first)
        || isEObject(second);
  }

  private static boolean isEObject(EClass eClass) {
    return "EObject".equals(eClass.getName())
        && eClass.getEPackage() != null
        && "http://www.eclipse.org/emf/2002/Ecore".equals(eClass.getEPackage().getNsURI());
  }

  private String qualified(EClassifier classifier) {
    return aliases.values().stream()
        .filter(alias -> alias.ePackage() != null)
        .filter(alias -> classifiers(alias).containsValue(classifier))
        .findFirst()
        .map(alias -> alias.name() + "::" + classifier.getName())
        .orElse(classifier.getName());
  }

  /**
   * Suggests the closest name, or lists the available ones.
   *
   * @param name the name as written
   * @param candidates the names that would have been valid
   * @param kind what the candidates are, e.g. "classes"
   */
  static String suggestion(String name, Collection<String> candidates, String kind) {
    if (candidates.isEmpty()) {
      return "There are no " + kind + " to choose from";
    }
    String lower = name.toLowerCase(Locale.ROOT);
    int best =
        candidates.stream()
            .filter(Objects::nonNull)
            .mapToInt(candidate -> similarity(lower, candidate))
            .min()
            .orElse(Integer.MAX_VALUE);
    if (best <= Math.max(1, name.length() / 3)) {
      List<String> closest =
          candidates.stream()
              .filter(Objects::nonNull)
              .filter(candidate -> similarity(lower, candidate) == best)
              .distinct()
              .limit(3)
              .map(candidate -> "'" + candidate + "'")
              .toList();
      return "Did you mean " + String.join(" or ", closest) + "?";
    }
    return "Available "
        + kind
        + ": "
        + String.join(", ", candidates.stream().limit(20).toList())
        + (candidates.size() > 20 ? ", …" : "");
  }

  /** Edit distance to the name or, for qualified names, to its last segment. */
  private static int similarity(String lowerName, String candidate) {
    String lower = candidate.toLowerCase(Locale.ROOT);
    return Math.min(
        distance(lowerName, lower),
        distance(lowerName, lower.substring(lower.lastIndexOf('.') + 1)));
  }

  /** Levenshtein distance. */
  private static int distance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }

  private void add(int offset, String message, String hint) {
    problems.add(ReactionsProcessor.problem(file, offset, message).withHint(hint));
  }
}
