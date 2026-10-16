package tools.vitruv.methodologist.setup.vsum.service;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import tools.vitruv.methodologist.setup.model.service.EcoreToGenmodelService;

/**
 * Aligns uploaded GenModels with the generated VSUM project.
 *
 * <p>The VSUM build places every GenModel into one model project named {@value
 * EcoreToGenmodelService#MODEL_PLUGIN_ID}. EMF writes the generated code (and the plugin files such
 * as {@code META-INF/MANIFEST.MF}) into the project named by the GenModel's {@code modelDirectory},
 * so a GenModel created for another project would generate into a path the MWE2 workflow does not
 * map and the build fails.
 *
 * <p>Uploaded files are also stored under names that can differ from the ECore file the GenModel
 * was created for (for example {@code KITModel.ecore} for a GenModel referencing {@code
 * Newuniversity.ecore}). The GenModel's package references are therefore pointed at the ECore file
 * it was uploaded with. The base package is left untouched, so the generated Java packages stay as
 * the user defined them.
 */
final class GenmodelProjectAligner {

  private GenmodelProjectAligner() {}

  /**
   * Rewrites {@code modelPluginID} and {@code modelDirectory} of the given GenModel file to the
   * shared model project and points its package references at the paired ECore file, saving the
   * GenModel in place.
   *
   * @param genmodelFile the GenModel file to align
   * @param ecoreFile the ECore file uploaded together with the GenModel
   * @throws IOException if a file cannot be loaded or saved, or the GenModel does not match the
   *     ECore file
   */
  static void align(File genmodelFile, File ecoreFile) throws IOException {
    ResourceSet resourceSet = createResourceSet();
    Resource genmodelResource = load(resourceSet, genmodelFile);

    if (genmodelResource.getContents().isEmpty()
        || !(genmodelResource.getContents().get(0) instanceof GenModel genModel)) {
      throw new IOException("File does not contain a GenModel: " + genmodelFile.getName());
    }

    if (!genModel.getGenPackages().isEmpty()) {
      Resource ecoreResource = load(resourceSet, ecoreFile);
      for (GenPackage genPackage : genModel.getGenPackages()) {
        repointEcorePackage(genPackage, ecoreResource, genmodelFile);
      }
    }
    genModel.getForeignModel().clear();
    genModel.getForeignModel().add(ecoreFile.getName());

    genModel.setModelPluginID(EcoreToGenmodelService.MODEL_PLUGIN_ID);
    genModel.setModelDirectory(EcoreToGenmodelService.MODEL_DIRECTORY);
    genmodelResource.save(Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
  }

  /**
   * Points a GenPackage at the package with the same URI fragment in the paired ECore file, so the
   * reference survives a different ECore filename.
   */
  private static void repointEcorePackage(
      GenPackage genPackage, Resource ecoreResource, File genmodelFile) throws IOException {
    EPackage current =
        (EPackage) genPackage.eGet(GenModelPackage.Literals.GEN_PACKAGE__ECORE_PACKAGE, false);
    if (current == null) {
      return;
    }
    String fragment =
        current.eIsProxy() ? ((InternalEObject) current).eProxyURI().fragment() : null;
    if (fragment == null && current.eResource() != null) {
      fragment = current.eResource().getURIFragment(current);
    }

    EObject target = fragment == null ? null : ecoreResource.getEObject(fragment);
    if (!(target instanceof EPackage ePackage)) {
      throw new IOException(
          "GenModel "
              + genmodelFile.getName()
              + " references a package that does not exist in the uploaded metamodel.");
    }
    genPackage.setEcorePackage(ePackage);
  }

  private static Resource load(ResourceSet resourceSet, File file) throws IOException {
    try {
      return resourceSet.getResource(URI.createFileURI(file.getAbsolutePath()), true);
    } catch (RuntimeException e) {
      throw new IOException("Failed to load " + file.getName(), e);
    }
  }

  private static ResourceSet createResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    Map<String, Object> factories =
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
    factories.put("ecore", new XMIResourceFactoryImpl());
    factories.put("genmodel", new XMIResourceFactoryImpl());
    resourceSet.getPackageRegistry().put(GenModelPackage.eNS_URI, GenModelPackage.eINSTANCE);
    return resourceSet;
  }
}
