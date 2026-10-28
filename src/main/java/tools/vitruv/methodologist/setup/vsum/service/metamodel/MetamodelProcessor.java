package tools.vitruv.methodologist.setup.vsum.service.metamodel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreValidator;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xml.type.XMLTypePackage;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.MethodologistSetupException;
import tools.vitruv.methodologist.setup.vsum.service.UploadedFile;
import tools.vitruv.methodologist.setup.vsum.service.VsumErrorCodes;
import tools.vitruv.methodologist.setup.vsum.service.project.ProjectLayout;

/**
 * Loads and validates uploaded Ecore metamodels and generates matching genmodels.
 *
 * <p>Genmodels are always generated rather than uploaded, so their plugin id, base package, and
 * model directory are guaranteed to match the generated project. Cross-references between uploaded
 * metamodels are resolved regardless of how they were written (relative path, {@code
 * platform:/resource} path, or nsURI) and normalized to relative references on save.
 */
@Slf4j
@Service
public class MetamodelProcessor {

  private static final String ECORE_EXTENSION = ".ecore";
  private static final String GENMODEL_EXTENSION = ".genmodel";
  private static final Map<String, Object> SAVE_OPTIONS =
      Map.of(XMLResource.OPTION_ENCODING, "UTF-8");

  static {
    GenModelPackage.eINSTANCE.eClass();
    EValidator.Registry.INSTANCE.put(EcorePackage.eINSTANCE, EcoreValidator.INSTANCE);
  }

  /**
   * Validates the uploaded metamodels, writes normalized ecores and generated genmodels into the
   * project's ecore directory, and describes the result.
   *
   * @param metamodels uploaded ecore files
   * @param projectRoot root of the generated project
   * @return one entry per uploaded metamodel, in upload order
   * @throws MethodologistSetupException when a metamodel is unparsable, invalid, or references a
   *     metamodel that was not uploaded
   * @throws IOException when writing the files fails
   */
  public List<GeneratedMetamodel> process(List<UploadedFile> metamodels, Path projectRoot)
      throws IOException {
    Path ecoreDirectory = ProjectLayout.ecoreDirectory(projectRoot);
    Files.createDirectories(ecoreDirectory);

    ResourceSet resourceSet = createIsolatedResourceSet();
    Map<UploadedFile, EPackage> packages = load(metamodels, resourceSet, ecoreDirectory);
    resolveCrossReferences(resourceSet, packages);
    validate(packages);

    resourceSet
        .getResources()
        .retainAll(packages.values().stream().map(EObject::eResource).toList());
    for (EPackage ePackage : packages.values()) {
      ePackage
          .eResource()
          .setURI(fileUri(ecoreDirectory.resolve(ePackage.getName() + ECORE_EXTENSION)));
    }

    Map<EPackage, GenModel> genModels = new LinkedHashMap<>();
    for (EPackage ePackage : packages.values()) {
      genModels.put(ePackage, createGenModel(ePackage, resourceSet, ecoreDirectory));
    }
    addUsedGenPackages(genModels);

    for (Resource resource : resourceSet.getResources()) {
      resource.save(SAVE_OPTIONS);
    }

    return genModels.entrySet().stream()
        .map(entry -> describe(entry.getKey(), entry.getValue()))
        .toList();
  }

  /**
   * Creates a resource set that never loads anything from disk or network. All metamodels must be
   * uploaded; Ecore's own packages come from the global package registry.
   */
  private ResourceSet createIsolatedResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.setURIConverter(
        new ExtensibleURIConverterImpl() {
          @Override
          public InputStream createInputStream(URI uri, Map<?, ?> options) throws IOException {
            throw new IOException("Loading '" + uri + "' is not allowed; it was not uploaded");
          }

          @Override
          public boolean exists(URI uri, Map<?, ?> options) {
            return false;
          }
        });
    return resourceSet;
  }

  private Map<UploadedFile, EPackage> load(
      List<UploadedFile> metamodels, ResourceSet resourceSet, Path ecoreDirectory) {
    List<String> errors = new ArrayList<>();
    Map<UploadedFile, EPackage> packages = new LinkedHashMap<>();
    EcoreResourceFactoryImpl factory = new EcoreResourceFactoryImpl();

    for (UploadedFile metamodel : metamodels) {
      Resource resource =
          factory.createResource(fileUri(ecoreDirectory.resolve(metamodel.fileName())));
      resourceSet.getResources().add(resource);
      try {
        resource.load(new ByteArrayInputStream(metamodel.content()), Map.of());
      } catch (IOException | RuntimeException e) {
        errors.add(metamodel.fileName() + ": not a valid Ecore file (" + rootMessage(e) + ")");
        continue;
      }
      if (resource.getContents().size() != 1
          || !(resource.getContents().get(0) instanceof EPackage ePackage)) {
        errors.add(metamodel.fileName() + ": must contain exactly one root EPackage");
        continue;
      }
      packages.put(metamodel, ePackage);
    }
    throwIfAny(errors);

    for (EPackage ePackage : packages.values()) {
      registerRecursively(ePackage, resourceSet.getPackageRegistry());
    }
    return packages;
  }

  private void registerRecursively(EPackage ePackage, EPackage.Registry registry) {
    if (ePackage.getNsURI() != null && !ePackage.getNsURI().isBlank()) {
      registry.put(ePackage.getNsURI(), ePackage);
    }
    ePackage.getESubpackages().forEach(sub -> registerRecursively(sub, registry));
  }

  /**
   * Resolves all proxies. References that do not resolve directly (e.g. absolute or {@code
   * platform:/resource} paths from the methodologist's workspace) are redirected to the uploaded
   * metamodel with the same file name.
   */
  private void resolveCrossReferences(
      ResourceSet resourceSet, Map<UploadedFile, EPackage> packages) {
    EcoreUtil.resolveAll(resourceSet);

    Map<String, Resource> resourcesByFileName = new HashMap<>();
    resourcesByFileName.put("Ecore.ecore", EcorePackage.eINSTANCE.eResource());
    resourcesByFileName.put("XMLType.ecore", XMLTypePackage.eINSTANCE.eResource());
    packages.forEach(
        (file, ePackage) -> resourcesByFileName.put(file.fileName(), ePackage.eResource()));

    List<String> errors = new ArrayList<>();
    Map<EObject, Collection<EStructuralFeature.Setting>> unresolved =
        new LinkedHashMap<>(EcoreUtil.UnresolvedProxyCrossReferencer.find(resourceSet));
    for (Map.Entry<EObject, Collection<EStructuralFeature.Setting>> entry : unresolved.entrySet()) {
      URI proxyUri = ((InternalEObject) entry.getKey()).eProxyURI();
      Resource candidate = resourcesByFileName.get(proxyUri.trimFragment().lastSegment());
      EObject target = candidate == null ? null : candidate.getEObject(proxyUri.fragment());
      if (target == null) {
        URI referencingResource =
            entry.getValue().iterator().next().getEObject().eResource().getURI();
        errors.add(
            String.format(
                "%s: references '%s', which is not part of the uploaded metamodels",
                referencingResource.lastSegment(), proxyUri));
        continue;
      }
      // derived features (e.g. eAllSuperTypes) are views on the stored features and follow them
      List.copyOf(entry.getValue()).stream()
          .filter(setting -> !setting.getEStructuralFeature().isDerived())
          .filter(setting -> setting.getEStructuralFeature().isChangeable())
          .forEach(setting -> EcoreUtil.replace(setting, entry.getKey(), target));
    }
    throwIfAny(errors);
  }

  private void validate(Map<UploadedFile, EPackage> packages) {
    List<String> errors = new ArrayList<>();
    Map<String, String> nsUris = new HashMap<>();
    Map<String, String> names = new HashMap<>();

    packages.forEach(
        (file, ePackage) -> {
          String fileName = file.fileName();
          if (ePackage.getName() == null || ePackage.getName().isBlank()) {
            errors.add(fileName + ": the root EPackage has no name");
          } else {
            String previous =
                names.putIfAbsent(ePackage.getName().toLowerCase(Locale.ROOT), fileName);
            if (previous != null) {
              errors.add(
                  String.format(
                      "%s: the package name '%s' is already used by %s",
                      fileName, ePackage.getName(), previous));
            }
          }
          if (ePackage.getNsURI() == null || ePackage.getNsURI().isBlank()) {
            errors.add(fileName + ": the root EPackage has no nsURI");
          } else {
            String previous = nsUris.putIfAbsent(ePackage.getNsURI(), fileName);
            if (previous != null) {
              errors.add(
                  String.format(
                      "%s: the nsURI '%s' is already used by %s",
                      fileName, ePackage.getNsURI(), previous));
            }
          }
          if (ePackage.getNsPrefix() == null || ePackage.getNsPrefix().isBlank()) {
            ePackage.setNsPrefix(ePackage.getName());
          }

          Diagnostic diagnostic = Diagnostician.INSTANCE.validate(ePackage);
          collectErrors(diagnostic, fileName, errors);
        });
    throwIfAny(errors);
  }

  private void collectErrors(Diagnostic diagnostic, String fileName, List<String> errors) {
    if (diagnostic.getChildren().isEmpty()) {
      if (diagnostic.getSeverity() >= Diagnostic.ERROR) {
        errors.add(fileName + ": " + diagnostic.getMessage());
      }
      return;
    }
    diagnostic.getChildren().forEach(child -> collectErrors(child, fileName, errors));
  }

  private GenModel createGenModel(EPackage ePackage, ResourceSet resourceSet, Path ecoreDirectory) {
    Resource resource =
        new XMIResourceFactoryImpl()
            .createResource(
                fileUri(ecoreDirectory.resolve(ePackage.getName() + GENMODEL_EXTENSION)));
    resourceSet.getResources().add(resource);

    GenModel genModel = GenModels.create(ePackage, ePackage.getName() + ECORE_EXTENSION);
    resource.getContents().add(genModel);
    return genModel;
  }

  /** Links each genmodel to the genmodels of the other uploaded metamodels it references. */
  private void addUsedGenPackages(Map<EPackage, GenModel> genModels) {
    genModels.forEach(
        (ePackage, genModel) -> {
          for (EObject target :
              EcoreUtil.ExternalCrossReferencer.find(Collections.singleton(ePackage)).keySet()) {
            EPackage targetPackage = containingPackage(target);
            if (targetPackage == null) {
              continue;
            }
            GenModel targetGenModel = genModels.get(rootPackage(targetPackage));
            if (targetGenModel == null || targetGenModel == genModel) {
              continue;
            }
            GenPackage targetGenPackage = targetGenModel.findGenPackage(targetPackage);
            if (targetGenPackage != null
                && !genModel.getUsedGenPackages().contains(targetGenPackage)) {
              genModel.getUsedGenPackages().add(targetGenPackage);
            }
          }
        });
  }

  private EPackage containingPackage(EObject object) {
    EObject current = object;
    while (current != null && !(current instanceof EPackage)) {
      current =
          current instanceof EClassifier classifier
              ? classifier.getEPackage()
              : current.eContainer();
    }
    return (EPackage) current;
  }

  private EPackage rootPackage(EPackage ePackage) {
    EPackage current = ePackage;
    while (current.getESuperPackage() != null) {
      current = current.getESuperPackage();
    }
    return current;
  }

  private GeneratedMetamodel describe(EPackage ePackage, GenModel genModel) {
    List<GeneratedMetamodel.GeneratedPackage> generatedPackages =
        genModel.getAllGenPackagesWithClassifiers().stream()
            .map(
                genPackage ->
                    new GeneratedMetamodel.GeneratedPackage(
                        genPackage.getNSURI(), genPackage.getQualifiedPackageInterfaceName()))
            .toList();
    return new GeneratedMetamodel(
        ePackage.getName(),
        ePackage.getNsURI(),
        ePackage.eResource().getURI().lastSegment(),
        genModel.eResource().getURI().lastSegment(),
        generatedPackages);
  }

  private static URI fileUri(Path path) {
    return URI.createFileURI(path.toAbsolutePath().toString());
  }

  private static String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() != null ? current.getMessage() : current.toString();
  }

  private static void throwIfAny(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new MethodologistSetupException(
          VsumErrorCodes.METAMODEL_ERROR,
          "The uploaded metamodels are invalid:\n- " + String.join("\n- ", errors));
    }
  }
}
