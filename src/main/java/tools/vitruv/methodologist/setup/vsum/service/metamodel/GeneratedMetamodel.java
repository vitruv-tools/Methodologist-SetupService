package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import java.util.List;

/**
 * A metamodel that was validated and placed into the generated project, together with its generated
 * genmodel.
 *
 * @param name name of the root EPackage
 * @param nsUri namespace URI of the root EPackage
 * @param ecoreFileName file name of the ecore inside the project's ecore directory
 * @param genmodelFileName file name of the genmodel inside the project's ecore directory
 * @param packages all packages EMF generates code for, including sub-packages
 */
public record GeneratedMetamodel(
    String name,
    String nsUri,
    String ecoreFileName,
    String genmodelFileName,
    List<GeneratedPackage> packages) {

  /** Makes the package list immutable. */
  public GeneratedMetamodel {
    packages = List.copyOf(packages);
  }

  /**
   * A package for which EMF generates a {@code Package} interface.
   *
   * @param nsUri namespace URI of the package
   * @param packageInterface fully qualified name of the generated {@code EPackage} interface
   */
  public record GeneratedPackage(String nsUri, String packageInterface) {}
}
