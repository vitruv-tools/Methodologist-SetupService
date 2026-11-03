package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import java.util.List;
import java.util.Optional;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreValidator;

/**
 * Turns the diagnostics of EMF's {@link EcoreValidator} into problems a methodologist can act on:
 * object dumps are replaced by element names, redundant diagnostics are dropped, and a hint says
 * how to fix the metamodel.
 */
final class EcoreDiagnostics {

  /**
   * A metamodel problem found by validation.
   *
   * @param element the element to locate the problem at
   * @param message what is wrong, without the element name
   * @param hint how to fix it, or {@code null}
   */
  record Finding(EObject element, String message, String hint) {}

  private EcoreDiagnostics() {}

  /**
   * Translates a leaf diagnostic.
   *
   * @param diagnostic a diagnostic without children
   * @return the problem, or empty if the diagnostic does not break the build or repeats another
   */
  static Optional<Finding> translate(Diagnostic diagnostic) {
    List<EObject> elements =
        diagnostic.getData().stream()
            .filter(EObject.class::isInstance)
            .map(EObject.class::cast)
            .filter(object -> !isEcoreMetamodel(object))
            .toList();
    if (diagnostic.getSeverity() == Diagnostic.WARNING && isNameClash(diagnostic)) {
      return Optional.of(nameClash(diagnostic.getCode(), elements));
    }
    if (diagnostic.getSeverity() < Diagnostic.ERROR || isMissingTypeOfTypedElement(diagnostic)) {
      return Optional.empty();
    }

    String message = diagnostic.getMessage();
    for (EObject element : elements) {
      message = message.replace(element.toString(), EcoreLocations.elementPath(element));
    }
    EObject element = elements.isEmpty() ? null : elements.get(0);
    String hint = null;
    if (EcoreValidator.DIAGNOSTIC_SOURCE.equals(diagnostic.getSource())) {
      switch (diagnostic.getCode()) {
        case EcoreValidator.WELL_FORMED_NAME ->
            hint =
                "Names must be valid Java identifiers: letters, digits and '_', not starting with"
                    + " a digit";
        case EcoreValidator.WELL_FORMED_INSTANCE_TYPE_NAME -> {
          if (message.contains("'null'")) {
            message = "the data type has no instance type name";
          }
          hint =
              "Set the instance type name to a Java type such as java.lang.String, or use an"
                  + " Ecore data type such as EString instead";
        }
        case EcoreValidator.VALID_TYPE ->
            hint =
                "Set the type, e.g. EString for an attribute or a class of the metamodels for a"
                    + " reference";
        case EcoreValidator.UNIQUE_FEATURE_NAMES ->
            hint = "Features are inherited from the super types; rename one of them";
        case EcoreValidator.UNIQUE_CLASSIFIER_NAMES,
            EcoreValidator.UNIQUE_ENUMERATOR_NAMES,
            EcoreValidator.UNIQUE_SUBPACKAGE_NAMES ->
            hint = "Rename one of them";
        case EcoreValidator.NO_CIRCULAR_SUPER_TYPES ->
            hint = "Remove the super type that leads back to this class";
        case EcoreValidator.INTERFACE_IS_ABSTRACT ->
            hint = "Make the class abstract as well, or clear 'interface'";
        case EcoreValidator.CONSISTENT_OPPOSITE_NOT_FROM_TYPE,
            EcoreValidator.CONSISTENT_OPPOSITE_NOT_MATCHING ->
            hint =
                "Set the opposite of both references to each other; each must point to the class"
                    + " that declares the other";
        case EcoreValidator.CONSISTENT_OPPOSITE_BOTH_CONTAINMENT ->
            hint = "Make only one of the two references a containment";
        case EcoreValidator.CONSISTENT_OPPOSITE_BAD_TRANSIENT ->
            hint = "Make the opposite reference transient as well";
        case EcoreValidator.CONSISTENT_UNIQUE -> hint = "Set 'unique' to true";
        case EcoreValidator.CONSISTENT_TYPE_CLASS_REQUIRED ->
            hint = "A reference must point to a class; use an attribute for data types";
        case EcoreValidator.CONSISTENT_TYPE_CLASS_NOT_PERMITTED ->
            hint = "An attribute must have a data type; use a reference to point to a class";
        default -> {
          // the message is all there is
        }
      }
    }
    return Optional.of(new Finding(element, message, hint));
  }

  /** EcoreValidator warns about names that differ only in case or '_'; EMF code clashes. */
  private static boolean isNameClash(Diagnostic diagnostic) {
    return EcoreValidator.DIAGNOSTIC_SOURCE.equals(diagnostic.getSource())
        && (diagnostic.getCode() == EcoreValidator.UNIQUE_FEATURE_NAMES
            || diagnostic.getCode() == EcoreValidator.UNIQUE_CLASSIFIER_NAMES
            || diagnostic.getCode() == EcoreValidator.UNIQUE_ENUMERATOR_NAMES);
  }

  /**
   * The data of a name clash is the owner (class, package, or enum) followed by the clashing
   * elements; features may be inherited from super types.
   */
  private static Finding nameClash(int code, List<EObject> elements) {
    EObject owner = elements.get(0);
    List<ENamedElement> clashing =
        elements.subList(1, elements.size()).stream()
            .filter(ENamedElement.class::isInstance)
            .map(ENamedElement.class::cast)
            .toList();
    // the last clashing element declared by the owner itself, else the owner
    EObject located =
        clashing.stream()
            .filter(element -> element.eContainer() == owner)
            .map(EObject.class::cast)
            .reduce((first, second) -> second)
            .orElse(owner);
    String message =
        String.format(
            "the %s %s differ only in upper/lower case or '_', so their generated Java names"
                + " clash",
            clashKind(code),
            String.join(
                " and ", clashing.stream().map(element -> "'" + element.getName() + "'").toList()));
    return new Finding(
        located,
        message,
        code == EcoreValidator.UNIQUE_FEATURE_NAMES
            ? "Rename one of them; features of the super types count as well"
            : "Rename one of them");
  }

  private static String clashKind(int code) {
    switch (code) {
      case EcoreValidator.UNIQUE_FEATURE_NAMES:
        return "features";
      case EcoreValidator.UNIQUE_ENUMERATOR_NAMES:
        return "enum literals";
      default:
        return "classifiers";
    }
  }

  /** "The required feature 'eAttributeType' … must be set" repeats "must have a type". */
  private static boolean isMissingTypeOfTypedElement(Diagnostic diagnostic) {
    return diagnostic.getData().contains(EcorePackage.Literals.EATTRIBUTE__EATTRIBUTE_TYPE)
        || diagnostic.getData().contains(EcorePackage.Literals.EREFERENCE__EREFERENCE_TYPE);
  }

  private static boolean isEcoreMetamodel(EObject object) {
    return EcoreUtil.getRootContainer(object) == EcorePackage.eINSTANCE;
  }
}
