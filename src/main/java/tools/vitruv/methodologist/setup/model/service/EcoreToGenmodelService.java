package tools.vitruv.methodologist.setup.model.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenResourceKind;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.setup.exception.GenmodelException;
import tools.vitruv.methodologist.setup.messages.ErrorMessages;
import tools.vitruv.methodologist.setup.messages.InfoMessages;

/** Service for generating GenModel files from ECore metamodels. */
@Slf4j
@Service
public class EcoreToGenmodelService {

  /**
   * Model plugin id shared by all generated GenModels, following the Methodologist template
   * (vitruv-tools/Methodologist-Template). The VSUM build creates a single model project named
   * after it, so every GenModel has to generate its code into that project. It is also used as the
   * base package, as in the template.
   */
  public static final String MODEL_PLUGIN_ID = "tools.vitruv.methodologisttemplate.model";

  /** Model directory inside the shared model project, as in the Methodologist template. */
  public static final String MODEL_DIRECTORY =
      "/" + MODEL_PLUGIN_ID + "/target/generated-sources/ecore";

  private static final String GENMODEL_EXTENSION = ".genmodel";
  private static final String ECORE_EXTENSION = ".ecore";
  private static final String ROOT_EXTENDS_CLASS =
      "org.eclipse.emf.ecore.impl.MinimalEObjectImpl$Container";
  private static final String IMPORTER_ID = "org.eclipse.emf.importer.ecore";

  /**
   * Generates a GenModel from an ECore file.
   *
   * <p>The ECore is written to a temporary working directory under its real base name and the
   * generated GenModel resource is placed alongside it, so all cross-references (for example {@code
   * ecorePackage} and {@code foreignModel}) deresolve to the actual ECore filename (for example
   * {@code model.ecore#/}) instead of an internal temporary name.
   *
   * @param ecoreFileBytes the ECore file content as bytes
   * @param ecoreFilename the original ECore filename
   * @return the generated GenModel as byte array
   * @throws GenmodelException if generation fails
   */
  public byte[] generateGenmodelFromEcore(byte[] ecoreFileBytes, String ecoreFilename) {
    log.info("Starting genmodel generation from ecore file: {}", ecoreFilename);

    String baseName = stripExtension(ecoreFilename);
    String ecoreName = baseName + ECORE_EXTENSION;
    String genmodelName = baseName + GENMODEL_EXTENSION;

    Path workDir = null;
    try {
      workDir = Files.createTempDirectory("ecore2genmodel_");
      Path ecorePath = workDir.resolve(ecoreName);
      Files.write(ecorePath, ecoreFileBytes);

      ResourceSet resourceSet = createResourceSet();

      EPackage ePackage = loadEcoreFile(resourceSet, ecorePath, ecoreName);
      GenModel genModel = createGenModel(ePackage, ecoreName);

      byte[] genmodelContent =
          serializeGenModel(resourceSet, genModel, workDir.resolve(genmodelName));
      log.info(InfoMessages.GENMODEL_GENERATION_SUCCESS);

      return genmodelContent;
    } catch (IOException e) {
      log.error("Failed to process ecore file", e);
      throw new GenmodelException(
          "ECORE_FILE_PROCESS_ERROR",
          String.format(ErrorMessages.ECORE_FILE_READ_ERROR, ecoreFilename),
          e);
    } finally {
      deleteQuietly(workDir);
    }
  }

  /**
   * Generates GenModels for several ECore files at once, writing each next to its ECore under the
   * ECore's base name (for example {@code model.ecore} gets {@code model.genmodel}).
   *
   * <p>The ECores are loaded into one resource set, so a metamodel that references another one (by
   * relative file reference or by nsURI) gets the other metamodel's GenPackage as used GenPackage.
   * Without it, EMF cannot qualify the referenced package interface and the generated code does not
   * compile.
   *
   * @param ecorePaths the ECore files to generate GenModels for
   * @return the written GenModel files, in the order of {@code ecorePaths}
   * @throws GenmodelException if an ECore is invalid or a GenModel cannot be written
   */
  public List<Path> generateGenmodelFiles(List<Path> ecorePaths) {
    ResourceSet resourceSet = createResourceSet();
    List<EPackage> ePackages = new ArrayList<>();
    for (Path ecorePath : ecorePaths) {
      EPackage ePackage = loadEcoreFile(resourceSet, ecorePath, ecorePath.getFileName().toString());
      if (ePackage.getNsURI() != null) {
        resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
      }
      ePackages.add(ePackage);
    }

    List<GenModel> genModels = new ArrayList<>();
    List<Path> genmodelPaths = new ArrayList<>();
    for (int index = 0; index < ecorePaths.size(); index++) {
      Path ecorePath = ecorePaths.get(index);
      String ecoreName = ecorePath.getFileName().toString();
      Path genmodelPath = ecorePath.resolveSibling(stripExtension(ecoreName) + GENMODEL_EXTENSION);
      GenModel genModel = createGenModel(ePackages.get(index), ecoreName);
      resourceSet
          .createResource(URI.createFileURI(genmodelPath.toAbsolutePath().toString()))
          .getContents()
          .add(genModel);
      genModels.add(genModel);
      genmodelPaths.add(genmodelPath);
    }

    linkUsedGenPackages(genModels);
    for (int index = 0; index < genModels.size(); index++) {
      writeGenModel(genModels.get(index), genmodelPaths.get(index));
    }
    log.info("Generated {} genmodel(s) for the VSUM build", genmodelPaths.size());
    return genmodelPaths;
  }

  /**
   * Adds the GenPackages of the other metamodels a GenModel's package references as used
   * GenPackages.
   *
   * @param genModels the GenModels generated together
   */
  private void linkUsedGenPackages(List<GenModel> genModels) {
    Map<EPackage, GenPackage> genPackageByEPackage = new HashMap<>();
    for (GenModel genModel : genModels) {
      for (GenPackage genPackage : genModel.getGenPackages()) {
        genPackageByEPackage.put(genPackage.getEcorePackage(), genPackage);
      }
    }

    for (GenModel genModel : genModels) {
      for (GenPackage genPackage : genModel.getGenPackages()) {
        for (EObject referenced :
            EcoreUtil.ExternalCrossReferencer.find(genPackage.getEcorePackage()).keySet()) {
          GenPackage used = genPackageByEPackage.get(EcoreUtil.getRootContainer(referenced));
          if (used != null
              && used.getGenModel() != genModel
              && !genModel.getUsedGenPackages().contains(used)) {
            genModel.getUsedGenPackages().add(used);
          }
        }
      }
    }
  }

  /**
   * Writes a GenModel to its file.
   *
   * @param genModel the GenModel to write; its resource must already be placed at {@code path}
   * @param path the target file
   * @throws GenmodelException if writing fails
   */
  private void writeGenModel(GenModel genModel, Path path) {
    try {
      Files.write(path, saveToBytes(genModel.eResource()));
    } catch (IOException e) {
      log.error("Failed to write genmodel: {}", path, e);
      throw new GenmodelException(
          "GENMODEL_SERIALIZE_ERROR", ErrorMessages.GENMODEL_GENERATION_ERROR, e);
    }
  }

  /**
   * Creates a resource set with the ecore and genmodel factories registered.
   *
   * @return a configured resource set
   */
  private ResourceSet createResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new XMIResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("genmodel", new XMIResourceFactoryImpl());
    GenModelPackage.eINSTANCE.eClass();
    return resourceSet;
  }

  /**
   * Loads an ECore file and returns the root EPackage.
   *
   * @param resourceSet the resource set to load into
   * @param ecorePath the ECore file to load
   * @param ecoreName the display name used in error messages
   * @return the root EPackage
   * @throws GenmodelException if the file cannot be loaded or is not a valid ECore package
   */
  private EPackage loadEcoreFile(ResourceSet resourceSet, Path ecorePath, String ecoreName) {
    log.debug("Loading ecore file: {}", ecorePath);

    try {
      URI uri = URI.createFileURI(ecorePath.toAbsolutePath().toString());
      Resource resource = resourceSet.getResource(uri, true);

      if (resource == null
          || resource.getContents().isEmpty()
          || !(resource.getContents().get(0) instanceof EPackage ePackage)) {
        throw new GenmodelException(
            "ECORE_FILE_INVALID",
            String.format(ErrorMessages.ECORE_FILE_INVALID_FORMAT, ecoreName));
      }

      return ePackage;
    } catch (GenmodelException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to load ecore file: {}", ecorePath, e);
      throw new GenmodelException(
          "ECORE_LOAD_ERROR", String.format(ErrorMessages.ECORE_FILE_READ_ERROR, ecoreName), e);
    }
  }

  /**
   * Creates a fully initialized GenModel from an EPackage.
   *
   * <p>{@link GenModel#initialize} reconciles the GenModel against the package, generating the
   * {@code genPackages}, {@code genClasses} and {@code genFeatures} entries so the produced file
   * mirrors what the Eclipse GenModel importer emits. The model plugin id, model directory and base
   * package follow the Methodologist template, so all generated GenModels share one model project.
   *
   * @param ePackage the root EPackage
   * @param ecoreName the ECore filename referenced by {@code foreignModel}
   * @return a new, initialized GenModel configured with the standard defaults
   * @throws GenmodelException if the nsURI cannot be determined
   */
  private GenModel createGenModel(EPackage ePackage, String ecoreName) {
    log.debug("Creating genmodel for package: {}", ePackage.getName());

    String nsUri = ePackage.getNsURI();
    if (nsUri == null || nsUri.isEmpty()) {
      throw new GenmodelException(
          "ECORE_NS_URI_MISSING", ErrorMessages.GENMODEL_GENERATION_NS_URI_ERROR);
    }

    GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
    genModel.getForeignModel().add(ecoreName);

    // initialize() reconciles the model with the package and resets several flags, so the
    // standard defaults are applied afterwards to make sure they are honored.
    genModel.initialize(Collections.singleton(ePackage));

    String modelName = capitalize(ePackage.getName());
    genModel.setModelDirectory(MODEL_DIRECTORY);
    genModel.setModelPluginID(MODEL_PLUGIN_ID);
    genModel.setModelName(modelName);
    genModel.setCreationIcons(false);
    genModel.setRootExtendsClass(ROOT_EXTENDS_CLASS);
    genModel.setCodeFormatting(true);
    genModel.setImporterID(IMPORTER_ID);
    genModel.setCopyrightFields(false);
    genModel.setImportOrganizing(true);

    GenPackage genPackage = genModel.getGenPackages().get(0);
    genPackage.setPrefix(modelName);
    genPackage.setBasePackage(MODEL_PLUGIN_ID);
    genPackage.setDisposableProviderFactory(true);
    // initialize() switches to an XML resource for packages with ExtendedMetaData annotations,
    // which generates XML resource classes the VSUM model project cannot compile. The template
    // uses the default resource, so keep it for every package.
    genPackage.setResource(GenResourceKind.NONE_LITERAL);

    return genModel;
  }

  /**
   * Serializes a GenModel to XMI, writing the resource next to the ECore so cross-references
   * deresolve to the plain ECore filename.
   *
   * @param resourceSet the resource set containing the loaded ECore
   * @param genModel the GenModel to serialize
   * @param genmodelPath the target genmodel path (its parent must match the ECore's directory)
   * @return the XMI content as byte array
   * @throws GenmodelException if serialization fails
   */
  private byte[] serializeGenModel(ResourceSet resourceSet, GenModel genModel, Path genmodelPath) {
    log.debug("Serializing genmodel to XMI");

    try {
      URI uri = URI.createFileURI(genmodelPath.toAbsolutePath().toString());
      Resource resource = resourceSet.createResource(uri);
      if (resource == null) {
        throw new IOException("Failed to create resource for genmodel serialization");
      }

      resource.getContents().add(genModel);
      return saveToBytes(resource);
    } catch (IOException e) {
      log.error("Failed to serialize genmodel", e);
      throw new GenmodelException(
          "GENMODEL_SERIALIZE_ERROR", ErrorMessages.GENMODEL_GENERATION_ERROR, e);
    }
  }

  /**
   * Serializes a GenModel resource to XMI.
   *
   * @param resource the GenModel resource
   * @return the XMI content as byte array
   * @throws IOException if serialization fails
   */
  private byte[] saveToBytes(Resource resource) throws IOException {
    Map<String, Object> options = new HashMap<>();
    options.put(XMLResource.OPTION_ENCODING, "UTF-8");
    // Serialize cross-document references (ecorePackage/ecoreClass/ecoreFeature) in the flat
    // attribute style used by canonical genmodel files, e.g.
    // ecoreClass="model.ecore#//Component".
    options.put(XMLResource.OPTION_USE_ENCODED_ATTRIBUTE_STYLE, Boolean.TRUE);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    resource.save(outputStream, options);
    return outputStream.toByteArray();
  }

  /**
   * Capitalizes the first character of a name, defaulting to {@code Model} when empty.
   *
   * @param name the name to capitalize
   * @return the capitalized name
   */
  private String capitalize(String name) {
    if (name == null || name.isEmpty()) {
      return "Model";
    }
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  /**
   * Strips a file extension from a filename, defaulting to {@code model} when empty.
   *
   * @param filename the filename
   * @return the base name without extension
   */
  private String stripExtension(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "model";
    }
    int dot = filename.lastIndexOf('.');
    String base = dot > 0 ? filename.substring(0, dot) : filename;
    return base.isEmpty() ? "model" : base;
  }

  /**
   * Recursively deletes a working directory, ignoring failures.
   *
   * @param dir the directory to delete (may be {@code null})
   */
  private void deleteQuietly(Path dir) {
    if (dir == null) {
      return;
    }
    try (var paths = Files.walk(dir)) {
      List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
      for (Path path : ordered) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      log.warn("Failed to clean up working directory: {}", dir, e);
    }
  }
}
