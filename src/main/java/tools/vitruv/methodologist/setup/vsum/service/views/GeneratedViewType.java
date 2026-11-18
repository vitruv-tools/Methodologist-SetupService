package tools.vitruv.methodologist.setup.vsum.service.views;

import java.util.Locale;

/**
 * A view type that NeoJoin for Vitruvius generates from an uploaded view definition. The names
 * mirror its code generator ({@code NeoJoinVitruvGenerator}, {@code ViewTypeSourceGenerator}, and
 * {@code NamingGenerator}), so that the generated project can refer to the generated classes.
 *
 * @param fileName the uploaded {@code .nj} file
 * @param name the name of the view type, under which the Vitruv server offers it
 * @param exportName the package the view definition exports its view metamodel to
 * @param nsUri the nsURI of the view metamodel
 * @param viewTypeClass the fully qualified name of the generated view type class
 * @param packageInterface the fully qualified name of the generated view metamodel package
 * @param genmodelPath the generated genmodel, relative to the view type module
 */
public record GeneratedViewType(
    String fileName,
    String name,
    String exportName,
    String nsUri,
    String viewTypeClass,
    String packageInterface,
    String genmodelPath) {

  /** Base package of the generated view types ({@code NamingGenerator.PACKAGE_BASE}). */
  public static final String PACKAGE_BASE = "neojoin.viewtypes";

  /** File extension of NeoJoin view definitions. */
  public static final String EXTENSION = ".nj";

  /** Delimiters NeoJoin removes when turning names into class names. */
  private static final String DELIMITERS = "_ -";

  /**
   * Describes the view type generated from a view definition.
   *
   * @param fileName the uploaded {@code .nj} file
   * @param exportName the package declared by {@code export <name> to "<nsUri>"}
   * @param nsUri the nsURI declared by the export
   * @param generatedSources the directory NeoJoin generates into, relative to the module
   * @return the view type
   */
  public static GeneratedViewType of(
      String fileName, String exportName, String nsUri, String generatedSources) {
    String baseName = baseName(fileName);
    String name = pascalCase(baseName);
    String javaPackage = PACKAGE_BASE + "." + exportName;
    return new GeneratedViewType(
        fileName,
        name,
        exportName,
        nsUri,
        javaPackage + "." + name + "ViewType",
        javaPackage + "." + pascalCase(exportName) + "Package",
        generatedSources + "/ecore/" + exportName + "/" + baseName + ".genmodel");
  }

  /**
   * Returns the file name without the {@code .nj} extension; NeoJoin derives the view type name
   * from it.
   *
   * @param fileName the file name
   * @return the base name
   */
  static String baseName(String fileName) {
    return fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION)
        ? fileName.substring(0, fileName.length() - EXTENSION.length())
        : fileName;
  }

  /**
   * Converts a name like NeoJoin does ({@code CaseUtils.toCamelCase(name, true, '_', ' ', '-')}):
   * the delimiters are removed, the letter after each of them and the first letter are upper case,
   * and all other letters are lower case, e.g. {@code my_View-2} → {@code MyView2}.
   *
   * @param name the name
   * @return the converted name
   */
  static String pascalCase(String name) {
    StringBuilder result = new StringBuilder(name.length());
    boolean capitalizeNext = true;
    for (char character : name.toLowerCase(Locale.ROOT).toCharArray()) {
      if (DELIMITERS.indexOf(character) >= 0) {
        capitalizeNext = true;
      } else {
        boolean capitalize = capitalizeNext || result.length() == 0;
        result.append(capitalize ? Character.toUpperCase(character) : character);
        capitalizeNext = false;
      }
    }
    return result.toString();
  }
}
