package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.codegen.ecore.genmodel.GenClassifier;
import org.eclipse.emf.codegen.ecore.genmodel.GenDataType;
import org.eclipse.emf.codegen.ecore.genmodel.GenEnum;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;

/**
 * Finds class names that EMF generates Java code for, but that the Java compiler rejects. EMF
 * escapes reserved words in feature, literal, and package names, but not in class names, and the
 * generated package and factory interfaces share the namespace of the classes.
 */
final class GeneratedNames {

  // keywords and literals, plus the restricted identifiers that may not name a type
  private static final Set<String> RESERVED =
      Set.of(
          "abstract",
          "assert",
          "boolean",
          "break",
          "byte",
          "case",
          "catch",
          "char",
          "class",
          "const",
          "continue",
          "default",
          "do",
          "double",
          "else",
          "enum",
          "extends",
          "final",
          "finally",
          "float",
          "for",
          "goto",
          "if",
          "implements",
          "import",
          "instanceof",
          "int",
          "interface",
          "long",
          "native",
          "new",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "short",
          "static",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "this",
          "throw",
          "throws",
          "transient",
          "try",
          "void",
          "volatile",
          "while",
          "true",
          "false",
          "null",
          "_",
          "var",
          "yield",
          "record",
          "sealed",
          "permits");

  private GeneratedNames() {}

  /**
   * Checks the class names of a metamodel against the Java code EMF generates for it.
   *
   * @param genModel the genmodel of the metamodel
   * @return the problems found, located at the classifiers
   */
  static List<EcoreDiagnostics.Finding> check(GenModel genModel) {
    List<EcoreDiagnostics.Finding> findings = new ArrayList<>();
    for (GenPackage genPackage : genModel.getAllGenPackagesWithClassifiers()) {
      Set<String> generated =
          Set.of(genPackage.getPackageInterfaceName(), genPackage.getFactoryInterfaceName());
      for (GenClassifier genClassifier : genPackage.getGenClassifiers()) {
        if (genClassifier instanceof GenDataType && !(genClassifier instanceof GenEnum)) {
          continue; // data types map to existing Java types
        }
        String name = genClassifier.getEcoreClassifier().getName();
        if (RESERVED.contains(name)) {
          findings.add(
              new EcoreDiagnostics.Finding(
                  genClassifier.getEcoreClassifier(),
                  String.format("'%s' is a reserved word in Java and cannot name a class", name),
                  String.format(
                      "Rename it, e.g. to '%s'; class names usually start with an upper-case"
                          + " letter",
                      capitalize(name))));
        } else if (generated.contains(name)) {
          findings.add(
              new EcoreDiagnostics.Finding(
                  genClassifier.getEcoreClassifier(),
                  String.format(
                      "the class '%s' has the name of the Java interface EMF generates for the"
                          + " package '%s'",
                      name, genPackage.getEcorePackage().getName()),
                  String.format(
                      "Rename the class; EMF generates %s and %s for the package",
                      genPackage.getPackageInterfaceName(), genPackage.getFactoryInterfaceName())));
        }
      }
    }
    return findings;
  }

  private static String capitalize(String name) {
    return name.equals("_") ? "Unnamed" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }
}
