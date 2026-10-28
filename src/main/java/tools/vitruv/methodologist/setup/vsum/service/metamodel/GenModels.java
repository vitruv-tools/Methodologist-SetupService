package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import java.util.Collections;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenResourceKind;
import org.eclipse.emf.codegen.ecore.genmodel.GenRuntimeVersion;
import org.eclipse.emf.ecore.EPackage;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Creates GenModels configured for the model project of the Methodologist Template. This is the
 * single place defining GenModel settings, shared by the VSUM build and the genmodel endpoint.
 */
public final class GenModels {

  private GenModels() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Creates and initializes a GenModel for a metamodel.
   *
   * @param ePackage the root package of the metamodel
   * @param ecoreFileName file name of the ecore, referenced as {@code foreignModel}
   * @return the configured GenModel, not yet attached to a resource
   */
  public static GenModel create(EPackage ePackage, String ecoreFileName) {
    GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
    // initialize() resets several flags, so all settings are applied afterwards
    genModel.initialize(Collections.singletonList(ePackage));
    genModel.getForeignModel().add(ecoreFileName);

    String modelName = capitalize(ePackage.getName());
    genModel.setModelName(modelName);
    genModel.setModelPluginID(ProjectLayout.MODEL_ARTIFACT_ID);
    genModel.setModelDirectory(
        "/" + ProjectLayout.MODEL_ARTIFACT_ID + "/" + ProjectLayout.GENERATED_ECORE_SOURCES);
    genModel.setImporterID("org.eclipse.emf.importer.ecore");
    genModel.setRootExtendsClass("org.eclipse.emf.ecore.impl.MinimalEObjectImpl$Container");
    // must be set explicitly, otherwise e.g. operation reflection is disabled; the build's EMF
    // code generator is newer and understands this version
    genModel.setRuntimeVersion(GenRuntimeVersion.EMF235);
    genModel.setOperationReflection(true);
    genModel.setImportOrganizing(true);
    genModel.setCodeFormatting(true);
    genModel.setCreationIcons(false);
    genModel.setCopyrightFields(false);

    for (GenPackage genPackage : genModel.getGenPackages()) {
      genPackage.setPrefix(modelName);
      genPackage.setBasePackage(ProjectLayout.MODEL_BASE_PACKAGE);
      genPackage.setDisposableProviderFactory(true);
      // initialize() switches packages with ExtendedMetaData annotations to an XML resource,
      // whose generated classes the model project cannot compile
      genPackage.setResource(GenResourceKind.NONE_LITERAL);
    }
    return genModel;
  }

  private static String capitalize(String name) {
    return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }
}
